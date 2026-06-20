#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <poll.h>
#include <signal.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#define MAX_MAPS 64
#define STR 128

static volatile sig_atomic_t g_stop = 0;
static const char *PIDFILE = "/data/local/tmp/rg505_mapperd.pid";
static const char *STOPFILE = "/data/local/tmp/rg505_mapperd.disabled";

typedef enum { MAP_AXIS, MAP_BUTTON } MapType;
typedef enum { TARGET_KEY, TARGET_MOUSE } TargetType;
typedef struct { MapType type; int src_code; int dir; TargetType target_type; int target_code; int active; char raw[STR]; } Mapping;

typedef struct {
    char event_name[STR];
    char output_name[STR];
    char output_mouse_name[STR];
    char mouse_axis_x[STR];
    char mouse_axis_y[STR];
    int mouse_center_x, mouse_center_y, mouse_deadzone, mouse_speed, mouse_interval_ms;
    int debug;
    Mapping maps[MAX_MAPS];
    int map_count;
} Config;

static void on_signal(int sig) { (void)sig; g_stop = 1; }
static void logf2(const char *fmt, ...) { va_list ap; va_start(ap, fmt); vfprintf(stdout, fmt, ap); fprintf(stdout, "\n"); fflush(stdout); va_end(ap); }
static void trim(char *s) { size_t n=strlen(s); while(n && (s[n-1]=='\n'||s[n-1]=='\r'||s[n-1]==' '||s[n-1]=='\t')) s[--n]=0; char *p=s; while(*p==' '||*p=='\t') p++; if(p!=s) memmove(s,p,strlen(p)+1); }
static void unquote(char *s) { trim(s); size_t n=strlen(s); if(n>=2 && ((s[0]=='"'&&s[n-1]=='"')||(s[0]=='\''&&s[n-1]=='\''))) { memmove(s,s+1,n-2); s[n-2]=0; } }

static int key_code(const char *s) {
    if(!s) return -1;
    struct { const char *n; int c; } t[] = {{"KEY_W",17},{"W",17},{"KEY_A",30},{"A",30},{"KEY_S",31},{"S",31},{"KEY_D",32},{"D",32},{"KEY_E",18},{"E",18},{"KEY_R",19},{"R",19},{"KEY_F",33},{"F",33},{"KEY_SPACE",57},{"SPACE",57},{"KEY_ENTER",28},{"ENTER",28},{"KEY_ESC",1},{"ESC",1},{"KEY_TAB",15},{"TAB",15},{"KEY_LEFTCTRL",29},{"CTRL",29},{"KEY_LEFTSHIFT",42},{"SHIFT",42},{"KEY_LEFTALT",56},{"ALT",56}};
    for(size_t i=0;i<sizeof(t)/sizeof(t[0]);i++) if(!strcmp(s,t[i].n)) return t[i].c;
    if(!strncmp(s,"KEY_",4)) return atoi(s+4);
    if(s[0]>='0'&&s[0]<='9') return atoi(s);
    return -1;
}
static int mouse_code(const char *s) { if(!strcmp(s,"MOUSE_LEFT")||!strcmp(s,"BTN_LEFT")) return BTN_LEFT; if(!strcmp(s,"MOUSE_RIGHT")||!strcmp(s,"BTN_RIGHT")) return BTN_RIGHT; if(!strcmp(s,"MOUSE_MIDDLE")||!strcmp(s,"BTN_MIDDLE")) return BTN_MIDDLE; return -1; }
static int abs_code(const char *s) { if(!strcmp(s,"ABS_X")) return ABS_X; if(!strcmp(s,"ABS_Y")) return ABS_Y; if(!strcmp(s,"ABS_Z")) return ABS_Z; if(!strcmp(s,"ABS_RX")) return ABS_RX; if(!strcmp(s,"ABS_RY")) return ABS_RY; if(!strcmp(s,"ABS_RZ")) return ABS_RZ; if(!strcmp(s,"ABS_HAT0X")) return ABS_HAT0X; if(!strcmp(s,"ABS_HAT0Y")) return ABS_HAT0Y; return -1; }
static int src_key_code(const char *s) { int k=key_code(s); if(k>=0) return k; if(!strcmp(s,"BTN_A")||!strcmp(s,"BTN_SOUTH")) return BTN_A; if(!strcmp(s,"BTN_B")||!strcmp(s,"BTN_EAST")) return BTN_B; if(!strcmp(s,"BTN_X")||!strcmp(s,"BTN_NORTH")) return BTN_X; if(!strcmp(s,"BTN_Y")||!strcmp(s,"BTN_WEST")) return BTN_Y; if(!strcmp(s,"BTN_TL")) return BTN_TL; if(!strcmp(s,"BTN_TR")) return BTN_TR; if(!strcmp(s,"BTN_SELECT")) return BTN_SELECT; if(!strcmp(s,"BTN_START")) return BTN_START; return -1; }

static void cfg_defaults(Config *c) { memset(c,0,sizeof(*c)); strcpy(c->event_name,"Xbox Wireless Controller"); strcpy(c->output_name,"RG505 D-pad WASD"); strcpy(c->output_mouse_name,"RG505 Mapper Mouse"); strcpy(c->mouse_axis_x,"ABS_Z"); strcpy(c->mouse_axis_y,"ABS_RZ"); c->mouse_center_x=0; c->mouse_center_y=0; c->mouse_deadzone=200; c->mouse_speed=2; c->mouse_interval_ms=8; }
static void parse_map(Config *c, const char *value) { if(c->map_count>=MAX_MAPS) return; char buf[256]; strncpy(buf,value,sizeof(buf)-1); buf[sizeof(buf)-1]=0; char *parts[5]={0}; int n=0; for(char *tok=strtok(buf,":"); tok && n<5; tok=strtok(NULL,":")) parts[n++]=tok; if(n<3) return; Mapping *m=&c->maps[c->map_count]; memset(m,0,sizeof(*m)); strncpy(m->raw,value,sizeof(m->raw)-1); if(!strcmp(parts[0],"axis") && n>=4) { m->type=MAP_AXIS; m->src_code=abs_code(parts[1]); m->dir=!strcmp(parts[2],"+")?1:-1; if(m->src_code<0) return; char *target=parts[3]; int k=key_code(target), mc=mouse_code(target); if(k>=0){m->target_type=TARGET_KEY;m->target_code=k;} else if(mc>=0){m->target_type=TARGET_MOUSE;m->target_code=mc;} else return; } else if(!strcmp(parts[0],"button") && n>=3) { m->type=MAP_BUTTON; m->src_code=src_key_code(parts[1]); if(m->src_code<0) return; char *target=parts[2]; int k=key_code(target), mc=mouse_code(target); if(k>=0){m->target_type=TARGET_KEY;m->target_code=k;} else if(mc>=0){m->target_type=TARGET_MOUSE;m->target_code=mc;} else return; } else return; c->map_count++; }
static int load_config(const char *path, Config *c) { cfg_defaults(c); FILE *f=fopen(path,"r"); if(!f) return -1; char line[512]; while(fgets(line,sizeof(line),f)) { trim(line); if(!line[0]||line[0]=='#') continue; char *eq=strchr(line,'='); if(!eq) continue; *eq=0; char *k=line,*v=eq+1; unquote(v); if(!strcmp(k,"EVENT_NAME")) strncpy(c->event_name,v,STR-1); else if(!strcmp(k,"OUTPUT_NAME")) strncpy(c->output_name,v,STR-1); else if(!strcmp(k,"OUTPUT_MOUSE_NAME")) strncpy(c->output_mouse_name,v,STR-1); else if(!strcmp(k,"MOUSE_AXIS_X")) strncpy(c->mouse_axis_x,v,STR-1); else if(!strcmp(k,"MOUSE_AXIS_Y")) strncpy(c->mouse_axis_y,v,STR-1); else if(!strcmp(k,"MOUSE_CENTER_X")) c->mouse_center_x=atoi(v); else if(!strcmp(k,"MOUSE_CENTER_Y")) c->mouse_center_y=atoi(v); else if(!strcmp(k,"MOUSE_DEADZONE")) c->mouse_deadzone=atoi(v); else if(!strcmp(k,"MOUSE_SPEED")) c->mouse_speed=atoi(v); else if(!strcmp(k,"MOUSE_INTERVAL_MS")) c->mouse_interval_ms=atoi(v); else if(!strcmp(k,"DEBUG")) c->debug=atoi(v); else if(!strncmp(k,"MAP_",4)) parse_map(c,v); } fclose(f); return 0; }

static FILE *start_hid(const Config *c) { FILE *h=popen("/system/bin/hid -","w"); if(!h) return NULL; fprintf(h,"{\"id\":1,\"command\":\"register\",\"name\":\"%s\",\"vid\":0x5247,\"pid\":0x0505,\"bus\":\"usb\",\"descriptor\":[0x05,0x01,0x09,0x06,0xa1,0x01,0x05,0x07,0x19,0xe0,0x29,0xe7,0x15,0x00,0x25,0x01,0x75,0x01,0x95,0x08,0x81,0x02,0x95,0x01,0x75,0x08,0x81,0x01,0x95,0x06,0x75,0x08,0x15,0x00,0x25,0x65,0x05,0x07,0x19,0x00,0x29,0x65,0x81,0x00,0xc0]}\n", c->output_name); fprintf(h,"{\"id\":2,\"command\":\"register\",\"name\":\"%s\",\"vid\":0x5247,\"pid\":0x0506,\"bus\":\"usb\",\"descriptor\":[0x05,0x01,0x09,0x02,0xa1,0x01,0x09,0x01,0xa1,0x00,0x05,0x09,0x19,0x01,0x29,0x03,0x15,0x00,0x25,0x01,0x95,0x03,0x75,0x01,0x81,0x02,0x95,0x01,0x75,0x05,0x81,0x01,0x05,0x01,0x09,0x30,0x09,0x31,0x15,0x81,0x25,0x7f,0x75,0x08,0x95,0x02,0x81,0x06,0xc0,0xc0]}\n", c->output_mouse_name); fprintf(h,"{\"id\":1,\"command\":\"delay\",\"duration\":750}\n{\"id\":1,\"command\":\"report\",\"report\":[0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00]}\n{\"id\":2,\"command\":\"report\",\"report\":[0x00,0x00,0x00]}\n"); fflush(h); return h; }
static int find_event_by_name(const char *name, char *out, size_t outsz) { FILE *p=popen("getevent -lp","r"); if(!p) return -1; char line[512], dev[128]="", nm[256]=""; int found=0; while(fgets(line,sizeof(line),p)) { if(!strncmp(line,"add device",10)) { if(dev[0]&&strstr(nm,name)){found=1;break;} char *slash=strstr(line,"/dev/input/event"); if(slash){sscanf(slash,"%127s",dev); nm[0]=0;} } else if(strstr(line,"name:")) { char *q=strchr(line,'"'); if(q){ char *r=strrchr(q+1,'"'); if(r){*r=0; strncpy(nm,q+1,sizeof(nm)-1);} } } } if(!found && dev[0]&&strstr(nm,name)) found=1; pclose(p); if(found){strncpy(out,dev,outsz-1); return 0;} return -1; }
static int wait_event(const char *name, char *out, size_t outsz) { for(int i=0;i<100;i++){ if(find_event_by_name(name,out,outsz)==0) return 0; usleep(100000); } return -1; }
static int send_ev(int fd, uint16_t type, uint16_t code, int32_t value) { struct input_event ev; memset(&ev,0,sizeof(ev)); gettimeofday(&ev.time,NULL); ev.type=type; ev.code=code; ev.value=value; return write(fd,&ev,sizeof(ev))==(ssize_t)sizeof(ev)?0:-1; }
static void sync_ev(int fd){ send_ev(fd,EV_SYN,SYN_REPORT,0); }
static int mouse_delta(int raw, int center, int dz, int speed){ int diff=raw-center; int ad=diff<0?-diff:diff; if(ad<=dz)return 0; int d=diff*speed/128; if(d==0)d=diff>0?1:-1; if(d>127)d=127; if(d<-127)d=-127; return d; }
static void set_target(const Mapping *m, int keyfd, int mousefd, int down) { int fd = m->target_type==TARGET_KEY ? keyfd : mousefd; send_ev(fd, EV_KEY, m->target_code, down); sync_ev(fd); }

int main(int argc, char **argv) { const char *cfgpath=argc>1?argv[1]:"/data/adb/modules/rg505_dpad_wasd/config"; Config cfg; load_config(cfgpath,&cfg); FILE *pf=fopen(PIDFILE,"w"); if(pf){fprintf(pf,"%d\n",getpid()); fclose(pf);} signal(SIGTERM,on_signal); signal(SIGINT,on_signal); logf2("rg505_mapperd starting native backend"); logf2("source name=%s mouse axes=%s/%s center=%d,%d dz=%d speed=%d interval=%d",cfg.event_name,cfg.mouse_axis_x,cfg.mouse_axis_y,cfg.mouse_center_x,cfg.mouse_center_y,cfg.mouse_deadzone,cfg.mouse_speed,cfg.mouse_interval_ms); FILE *hid=start_hid(&cfg); if(!hid){logf2("ERROR starting hid"); return 1;} char srcpath[128], keypath[128], mousepath[128]; if(wait_event(cfg.event_name,srcpath,sizeof(srcpath))||wait_event(cfg.output_name,keypath,sizeof(keypath))||wait_event(cfg.output_mouse_name,mousepath,sizeof(mousepath))){logf2("ERROR finding event nodes");return 2;} logf2("events source=%s keyboard=%s mouse=%s",srcpath,keypath,mousepath); int src=open(srcpath,O_RDONLY|O_CLOEXEC); int keyfd=open(keypath,O_WRONLY|O_CLOEXEC); int mousefd=open(mousepath,O_WRONLY|O_CLOEXEC); if(src<0||keyfd<0||mousefd<0){logf2("ERROR open fds: %s",strerror(errno));return 3;} int mx_code=abs_code(cfg.mouse_axis_x), my_code=abs_code(cfg.mouse_axis_y); int mdx=0, mdy=0; struct pollfd pfd={.fd=src,.events=POLLIN}; while(!g_stop && access(STOPFILE,F_OK)!=0){ int pr=poll(&pfd,1,cfg.mouse_interval_ms); if(pr>0 && (pfd.revents&POLLIN)){ struct input_event ev; while(read(src,&ev,sizeof(ev))==(ssize_t)sizeof(ev)){ if(ev.type==EV_ABS){ for(int i=0;i<cfg.map_count;i++){ Mapping *m=&cfg.maps[i]; if(m->type==MAP_AXIS && m->src_code==ev.code){ int dir=0; if(ev.code==ABS_HAT0X||ev.code==ABS_HAT0Y) dir=ev.value; else { int center = ev.code==mx_code?cfg.mouse_center_x:(ev.code==my_code?cfg.mouse_center_y:0); int diff=ev.value-center; if(diff>cfg.mouse_deadzone)dir=1; else if(diff<-cfg.mouse_deadzone)dir=-1; } int active=(dir==m->dir); if(active!=m->active){ set_target(m,keyfd,mousefd,active); m->active=active; } } } if(ev.code==mx_code) mdx=mouse_delta(ev.value,cfg.mouse_center_x,cfg.mouse_deadzone,cfg.mouse_speed); if(ev.code==my_code) mdy=mouse_delta(ev.value,cfg.mouse_center_y,cfg.mouse_deadzone,cfg.mouse_speed); } else if(ev.type==EV_KEY){ for(int i=0;i<cfg.map_count;i++){ Mapping *m=&cfg.maps[i]; if(m->type==MAP_BUTTON && m->src_code==ev.code){ int active=ev.value!=0; if(active!=m->active){ set_target(m,keyfd,mousefd,active); m->active=active; } } } } if(ev.type==EV_SYN) break; } } if(mdx||mdy){ if(mdx) send_ev(mousefd,EV_REL,REL_X,mdx); if(mdy) send_ev(mousefd,EV_REL,REL_Y,mdy); sync_ev(mousefd); } }
 for(int i=0;i<cfg.map_count;i++) if(cfg.maps[i].active) set_target(&cfg.maps[i],keyfd,mousefd,0); logf2("rg505_mapperd stopping"); close(src); close(keyfd); close(mousefd); if(hid) pclose(hid); unlink(PIDFILE); return 0; }
