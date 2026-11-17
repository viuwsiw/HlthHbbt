#ifndef HLTH_TUNNEL_BRIDGING_H
#define HLTH_TUNNEL_BRIDGING_H

int hlth_tun_fd(void);

// hev-socks5-tunnel (статическая библиотека): TCP/IP-стек, пакеты → SOCKS5.
int hev_socks5_tunnel_main_from_str(const unsigned char *config_str, unsigned int config_len, int tun_fd);
void hev_socks5_tunnel_quit(void);

#endif
