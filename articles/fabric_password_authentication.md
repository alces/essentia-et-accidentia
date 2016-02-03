---
layout: post
title: 'Using Password Authentication in Fabric'
date: 2016-01-11 17:14
comments: true
categories: [Python, fabric, pycrypto]
---
![](http://uploads6.wikiart.org/images/gustave-dore/don-quixote-30.jpg)

In my opinion, it's a lot simple to use public-key authentication in Fabric and don't bother about passwords at all. But in the real world, time by time happens that you have to enter passwords (for example, you're planning to login at the hundreds of hosts only once and don't want to spend a half of the day adding your public SSH key to them, or might be your environment simply requires using sudo with password instead of logging in as root.) For such kind of cases, it worth to learn about how Fabric can relieve inconvenience bound with entering passwords.

## 1. Basic steps

The simplest way to be authenticated by password via Fabric is to add this password to command line:

```
fab --password=vagrant
```

or (for those who loves short forms) simply:

```
fab -p vagrant
```

This password will be used both for connecting to hosts via SSH and for running commands using Fabric **sudo** function, but it wouldn't be used if you wrap a shell command containing sudo into Fabric **run** function.

If you don't like to enter password every time you're executing your **fabfile.py**, it's possible to write it once inside it by assigning **password** element of **env** dictionary:

```
from fabric.api import env

env.password = 'vagrant'
```

In the case when your environment is so complicated that it requires different passwords for logging in and for running sudo (or another usecase requiring entering more than one password happens.) some statements in **fabfile.py** can be wrapped into **settings** context manager. For example:

```
from fabric.api import run, settings, sudo

def run_test():
  run('ls /home')
  with settings(password = 'vagrant123'):
    sudo('ls /root')
```

As almost everebody knows, hard-coding your passwords in the scripts is a very bad idea, because:

1. it isn't secure at all

2. you've to change slew of your scripts in an event of changing the password

Let's consider, how we can hide our password from too many of the interested eyes.

## 2. Getting password from terminal

I believe, the most secure way to get a password into a script is to ask user to enter it. Python standard library already has a module knowing how to read password from a control terminal without echoing its characters back:

```
import getpass
env.password = getpass.getpass()
```

In the terms of convenience, this way is the same as adding password to Fabric command line, but it seems a lot more secure, because your password couldn't be seen in the output of **ps** command and in the shell history. If you have to enter password once for a script running against hundreds of hosts, it looks like a sensible burden, but doing so while debuging your script against only one host seems too boring.

## 3. Storing password in some secret place

In order to make your life a bit easier, your might want to store your password in a file which:

1. never will be added to version control

2. isn't readable for others

A paranoid person can ever check this file's permissions before reading from it:

```
import os

passPath = os.path.join(os.environ['HOME'], '.fabric_passwd')
passStat = os.stat(passPath)
assert passStat.st_uid = os.getuid() and passStat.st_mode & 077 == 0

env.password = open(passPath).read()
```

This approach seems to be quite convenient, because the same file with password(s) stored inside can be used by many scripts (not necessary written in Fabric or even in Python), and (at least, in my opinion) is reasonably secure. The same approach to storing passwords is used, for example, in Subversion client on Unix-like platforms. But if you hate storing unencrypted passwords anywhere (or symply have no right to do that), let's look into how a password can be encrypted before saving to a file (and, of course, decrypted after a reading from there.)

## 4. Encrypting password

Fabric doesn't has any cryptographic functions by itself, but any Python cryptographic library can be used in order to fit the bill. The one I've heard more often in PyCrypto, so let's use it. All the tinkering with password encryption/decryption will be performed locally, so the library should be installed only on our client machine, not the servers we're planning to work with. On the modern Linux systems, the simplest way to install PyCrypto is by using **pip**:

```
pip install pycrypto
```

If you run your Fabric scripts from RHEL machine, consider using easy_install instead:

```
easy_install pycrypto
```

Note that some native libraries are being build during the process of the installation, so the commands above won't proceed successfully without a C compiller and the developer versions of Python and GMP libraries. On a RedHat-based system, these dependencies can be installed by running:

```
yum install -y gcc gmp-devel python-devel
```

First thing we've to do in order to encrypt or descript something is initializinig an object presenting a given cipher (in this example, we'll use Triple DES.) The only required argument of **new** method is a secret key, which - in the case of Triple DES - can be 8, 16, or 24 bytes long:

```
from Crypto.Cipher import DES3

des = DES3.new(open('/etc/protocols').read()[:24])
```

The Fabric task bellow encrypt a password and writes it into a file:

```
from fabric.api import task

@task
def encrypt(aSecret):
  encPass = des.encrypt(aSecret.center(32))
  open(passPath, 'w').write(encPass)
```

Note that a string we're going to crypt by Triple DES must be a multiple of 8 in length, so we've to add spaces in order to make it 32 bytes long (I hope, your real world password is a bit shorter than 32 characters.)

The following lines read an encrypted password from a file, decrypt it, and remove the leading and tailing spaces from the result:

```
aSecret = open(passPath).read()
env.passwd = des.decrypt(aSecret).strip()
```

If your password begins or ends with a space (personally, I've never run into such cases), it's possible to use another symbol as an additional argument of **center** and **strip** methods. For example:

```
encPass = des.encrypt(aSecret.center(32, '#'))
```

and:

```
env.passwd = des.decrypt(aSecret).strip('#')
```

I should mentioned that a string encrypted by Triple DES consists of non-ASCII symbols, so in order to save it in a some kind of text-based configuration file (INI/JSON/YAML, etc.) it should be converted into text before saving into and back into binary after reading from a file. For example, we can use Python **base64** module for this purpose:

```
import base64

open(passPath, 'w').write(base64.b64encode(encPass))

...

env.passwd = des.decrypt(base64.b64decode(aSecret)).strip()
```

As you might have already noticed, we use the first 24 bytes from a publicly accessible (and rarely changed) file as our encryption key. This approach can't be called secure, because everyone who can read our **fabfile.py** could construct the same key. So, the problem of hiding our password becomes a problem of hiding the key it has been encrypted by. The only real difference is that somebody having a key can't use it to connect our servers directly, but has to find somewhere our ecrypted password (and it isn't hard, because we don't bother to hide it anymore, eh?) and figure out which cipher we've used in order to encrypt it (and it isn't very hard too as long as a malicious somebody has read access to our **fabfile.py**.) So, the only truly secure way to store a key is to ask for entering it interactively during every script's run. And it isn't an issue with Triple DES cipher only, it's the way how cryptography with secret key works. So decide by yourself, whether all the fuss with PyCrypto would worth it, if we finally ran into the same issue in a sligthly different form.

If you really decide to encrypt your password(s) and want to use an algorithm other than Triple DES, be aware that AES and plain old DES are also supported by PyCrypto (you should import `Crypto.Cipher.AES` and `Crypto.Cipher.DES` accordingly.) The former supports 32-byte long keys, the latter - only the 8-byte long ones. For the futher reading on PyCrypto, consider [this arcticle](http://www.laurentluce.com/posts/python-and-cryptography-with-pycrypto).

-----

Tested against `Fabric 1.10.2`, `Python 2.7.8`, and `pycrypto-2.6.1`