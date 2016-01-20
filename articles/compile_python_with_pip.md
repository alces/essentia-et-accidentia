---
layout: post
title: 'Installing a Fresh Python Version on RHEL 6/7'
date: 2016-01-20 11:08
comments: true
categories: [Python, Linux, virtualenv, pip, Ansible]
---
![](http://uploads7.wikiart.org/images/gustave-dore/don-quixote-117.jpg)

Linux distributions from RHEL-6 family are already shipped with Python-2.6.6 out-of-box, which works okay in the most practical cases, and RHEL-7 has - even better - Python-2.7.5 on board. But could you imagine any fervent Python lover satisfied by these outmoded releases? Neither do I, so in this article we're going to install on our RHEL the latest Python compiled from sources. The process will be described in the terms of Ansible tasks, because - in comprasion with the plain old shell commands - they seem to covey their inital purpose in much more clear manner. In order to get rid of large chunks of repeating text, our tasks will use some variables. Their values I've set in my testing environment are the following (of course, you might want to change some of them):

```
  vars:
    python_version: 2.7.11
    python_major_version: "{{ python_version.split('.') | first() }}"
    python_install_dir: "/opt/python-{{ python_version }}"
    tmp_dir: /var/tmp
    python_srcname: "Python-{{ python_version }}"
    python_arcname: "{{ python_srcname }}.tgz"
    python_build_dir: "{{ tmp_dir }}/{{ python_srcname }}"
```

The first thing we have to do is installing Python's build dependencies:

```
  - name: Install Python's build prerequisites
    yum:
      name: "{{ item }}"
    with_items:
    - gcc
    - make
    - openssl-devel
    - readline-devel
```

Technically speaking, you could build Python having **gcc** and **make** only, but I think, you wouldn't get much fun from Python built without openssl and readline. Without openssl, such a widely-used tool as pip wouldn't work at all, and without readline you couldn't use arrow keys in order to navigate through commands' history while running Python interpreter interactively. You also might think of another developer packages required for your specific use case (e.g., **bzip2-devel** or **sqlite-devel**.)

Next, let's download an archive with Python's sources from the project's site and unpack it:

```
  - name: Download Python source
    get_url:
      url: "https://www.python.org/ftp/python/{{ python_version }}/{{ python_arcname }}"
      dest: "{{ tmp_dir }}"

  - name: Unpack Python sources
    unarchive:
      src: "{{ tmp_dir }}/{{ python_arcname }}"
      dest: "{{ tmp_dir }}"
      copy: no
```

The way Python can be built resemble to the one of many other Unix programs: **configure**, then **make**. Note that while using Ansible **shell** module, we've to set its **creates** argument in order to make our tasks somewhat idempotent:

```
  - name: Configure Python
    shell: "./configure --prefix={{ python_install_dir }}"
    args:
      chdir: "{{ python_build_dir }}"
      creates: "{{ python_build_dir }}/Makefile"

  - name: Compile and install Python
    shell: "make install"
    args:
      chdir: "{{ python_build_dir }}"
      creates: "{{ python_bin_dir }}"
```

If you don't like meaningless files cluttering temporary directories (and if the host you're installing Python onto is going to live longer than a hour,) sources we're not need anymore can be safely purged:

```
  - name: Delete Python sources
    file:
      name: "{{ item }}"
      state: absent
    with_items:
    - "{{ tmp_dir }}/{{ python_arcname }}"
    - "{{ python_build_dir }}"
```

If you've choose to install the latest version from 2.7 branch of Python, you likely might want to add **pip** to this installation (latest releases of Python 3 already have **pip** on board.) So, it would be wise to put the tasks installing pip into a separate file and conditionally include this file only in the case when we're working with version 2 of Python:

```
  - include: pip_install.yml
    when: python_major_version == '2'
```

The actual content of `pip_install.yml` would be the following:

```
- name: Get pip install script
  get_url:
    url: "https://bootstrap.pypa.io/get-pip.py"
    dest: "{{ tmp_dir }}"

- name: Install pip
  command: "{{ python_bin_dir }}/python {{ tmp_dir }}/get-pip.py"
  args:
    creates: "{{ python_bin_dir }}/pip"
```

Note that we use the newly installed Python to run the last command instead of default `/usr/bin/python` shipped with our system. We do so in to order to install **pip** not into the default `/usr/bin` but under **python_install_dir**.

But you might don't want to install **pip** at all, because if you were to use **virtualenv**, it would install **pip** automatically for each newly creating environment. The simplest way to install **virtualenv** I know is the following:

```
  - name: Install easy_install
    yum:
      name: python-setuptools

  - name: Install virtualenv
    easy_install:
      name: virtualenv
```

(we use **easy_install** because RHEL-6/7 doesn't have RPM containing **pip** in its standard repositories)

Having **virtualenv** installed, we can create new Python environments, bind our latest Python to them, and install packets using **pip** even though we have no **pip** known for a system's default Python. For example:

```
virtualenv ansible19
virtualenv -p /opt/python-2.7.9/bin/python ansible19
source ansible19/bin/activate
pip install ansible
```

But if you want to install some package having binary dependencies into a virtual environment, you still have to install the libraries they depend on system-wide (e.g., `pip install pycrypto` will succeed only if you've **gmp-devel** RPM installed.)

The last thing I want to add is that Ansible has [pip module](http://docs.ansible.com/ansible/pip_module.html), which already knows how to install Python packages into virtual environments, even how to automatically create virtual environments in a case they don't exist, but it doesn't support binding the target environment to some non-default Python installation (they said this feature would be implemented in 2.0.0 release of Ansible.) Nothing is perfect in the world!

-----

Tested against `ansible 1.9.4`, `CentOS 7.1.1503`, `OEL 6.4`, `Python 2.7.11`, `Python 3.5.1`