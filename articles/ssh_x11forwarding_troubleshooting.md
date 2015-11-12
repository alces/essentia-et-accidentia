---
layout: post
title: 'Where to Look When X11 Forwarding via Ssh Doesn't Work'
date: 2015-11-12 10:52
comments: true
categories: [Linux, SSH, x11forwarding, solaris]
---
![](http://uploads6.wikiart.org/images/gustave-dore/don-quixote-13.jpg)

Oftentimes, when you try to connect a newly installed system via `ssh -X someserver` in order to run one weird GUI application on it, you might get a jeering message such as this:

```
X11 forwarding request failed on channel 0
```

Well, as an old good admiral put it once, "There's something wrong with our bloody ships today". Let's find out what exactly could be wrong.

1. the most obvious case. A line saying `X11Forwarding yes` should be present in `/etc/ssh/sshd_config` on your server. Once again, it sounds a bit blankly, but since this parameter is set to `no` by default, it's worth to double-check (and don't forget to restart the **sshd** server after changing its config file.)

2. if it still doesn't work, try adding `-v` flag to your `ssh -X` command-line. If you saw a line like this:
```
debug1: Remote: No xauth program; cannot forward with spoofing.
```
right before our nasty `X11 forwarding request failed ...` message, it would simply mean that you have no **xauth** binary installed on your server. On RHEL-5/6 (and their derivatives) this command lives in package named `xorg-x11-xauth`, on Solaris 11 the package name is as simple as `xauth`, but on the earlier Solarises in order to get **xauth** you should install no less than `SUNWxwplt`.

3. a more cryptic case. Happens only on the relatively new systems (e.g. I've never run into it on RHEL-5.) If you got enigmatic lines like this:
```
error: Failed to allocate internet-domain X11 display socket.
```
in your `/var/log/secure` (in a case of RHEL-derivatives, other systems could store that kind of messages in a some other place), it would mean that **sshd** on your server trying to set up X11 connection through **IPv6**, while only **IPv4** is actually working. In order to tell **sshd** explicitly that it must work over **IPv4** you could add `AddressFamily inet` line to your `/etc/ssh/sshd_config` (it'll work that way on RHEL-6 and its derivatives; if a similiar error occurs on Solaris 11, you could achieve the same effect by using `ListenAddress 0.0.0.0` line.)

If you got down here without any significant result, it migth be a problem in your setup on client side. Or wouldn't it be a good idea to simply look for advise in some another blog?

Good luck!

---

Tested against `OEL 5.8`, `OEL 6.4`, `SunOS 5.10`, `SunOS 5.11` (server) and `Fedora 21` (client)