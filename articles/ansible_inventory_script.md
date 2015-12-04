---
layout: post
title: 'Writing Inventory Script for Ansible'
date: 2015-11-24 11:35
comments: true
categories: [Python, Ansible, inventory]
---
![](https://upload.wikimedia.org/wikipedia/commons/8/84/Barbebleue.jpg)

As for me, the most fascinating part of working with servers through Ansible is that a file containg a list of hosts (set by configuration parameter named `invertory` in the recent versions of Ansible or `hostfile` in the older ones) can be a program written in any imaginable programming language. In order to turn this behavior on, you simply should make a file pointed by `inventory` directive executable. Unlike its simple non-executable form which has plain old [INI format](https://en.wikipedia.org/wiki/INI_file), an exacutable inventory must return JSON data. In this article, we're going to write a sample inventory script in Python, the language Ansible itself is written in.

Imagine, we have a pool of servers hosting Git and Subversion repositories: three-node clusters for the both VCS in four locations (Birmingham, Jakarta, Montovideo, and Mumbai.) Name of production servers' matches `^(git|svn)LOCnode[1-3]$` pattern  (where `LOC` is a location code - a three-consonants abbreviation of the city's name.) Imagine again, we also have a lonely server named `svntest01`, where we usually compile and test new versions of Subversion (because Subversion RPMs shipped with CentOS are, unfortunately, quite outdated.) We're planning to create a very simple playbook generating `/etc/profile.d/svn.sh` files for our Subversion servers - and `/etc/profile.d/git.sh` for Git the ones. Jinja2 template for the both kind of files will be the same:

```
# content of vcs.sh.js2
export PATH={{ vcs_path }}/bin:$PATH
export LD_LIBRARY_PATH={{ vcs_path }}/lib:$LD_LIBRARY_PATH
```

Mission of assigning Ansible variable `vcs_path` to a some meaningful value for each of our hosts should be accomplished by the inventory we're going to write.

By convention, an inventory script for Ansible must be capable to operate in two modes:

1. getting a list of host's varibles (activated by passing `--host a.name.of.host` arguments via command line);

2. getting full list of groups countaining hosts, variables, and children groups (a single `--list` argument should be passed via command line.)

In the both modes, output has a form of JSON dictionary (in a case of the host's variables, it can be empty.)

Let's start by implementing the first mode, as a simpler one. All the our production servers have standard configuration, so must have no individual host's variables. Only for `svntest01`, where experemental Subversion releases are run, `vcs_path` should be set individually:

```
import json
import sys

if len(sys.argv) == 3 and sys.argv[1] == '--host':
  if sys.argv[2] == 'svntest01':
    print json.dumps({
      'vsc_path': '/usr/local/subversion-1.8.14'
    })
  else:
   print '{}'
  sys.exit(0)
```

The second part of our task is going to be a bit more interesting. While using `--list` option, an output generally looks that way:

```
{
  'group1': {
    'hosts': ['host1', 'host2', ... ],
    'childrens': ['group2', 'group3', ...],
    'vars': {
      'var1': 'value1',
      'var2': 'value2',
      ...
    },
    'group2': {
      ...
    }
}
```

Also, Ansible has a group created automatically by default and consisting of all the hosts known to it - and its name by no surprise is `all`. Placing hosts or childrens in this group makes little sense, but it's the right place for defining some global variables. For example, if you wanted to place your sudo password into inventory, you could do so not by hard-coding its value in the script, but by reading a string from a secret file you're not planning to put under version control:

```
import os

groups = {}

groups.update({
  'all': {
    'vars': {
      'ansible_sudo_pass' = read(
      	os.path.join(os.environ['HOME'], '.ansible.pass')
      ).readline().rsplit()
    }
  }
}
```

In order to create the groups containting our production hosts, let's first generate a list of the locations' abbreviations:

```
import re

locations = ['Birmingham', 'Jakarta', 'Montovideo', 'Mumbai']
abbrs = map(lambda s: re.sub(r'[aeiouy]', '', s.lower())[:3], locations)
```

From this list we can populate groups of the similar nodes at the same locations:

```
for vcs in ('git', 'svn'):
  for loc in abbrs:
    groups['%s_%s' % (vcs, loc)] = {
      'hosts': ['%s%snode%d' % (vcs, loc, nod) for nod in (1, 2, 3)]
    }
```

Technically speaking, a lists of hosts or childrens can be not only Python **lists** but **tuples** too, becase `json.dumps` converts a list and a tuple in the same representation (but Python **sets** aren't supported by `json.dumps`, so they must be explicitly converted by `list()` or `tuple()` function.)

Our overarching groups don't contain any hosts - only childrens:

```
for vcs in ('git', 'svn'):
  groups[vcs] = {
    'childrens': ['%s_%s' % (vcs, loc) for loc in abbrs]
  }
```

These groups share common values of `vcs_path` variable:

```
groups['git']['vars'] = {
  'vcs_path': '/opt/gitlab/embedded'
}
groups['svn']['vars'] = {
  'vcs_path': '/opt/CollabNet_Subversion'
}
```

In order to make our inventory a bit more interesting, let's imagine that our SVN servers in old good England are really old, and their version of `/usr/bin/python` (which Ansible uses to run its tasks by default) isn't compatible with our modern Ansible. So, we can say Ansible to use non-standard Python installation on those servers:

```
groups['svn_brm']['vars'] = {
 'ansible_python_interpreter': '/opt/tools/python-2.7.9/bin/python'
}
```

Having all this setup done, we can implement `--list` command-line key simply by returning JSON dump of our `groups` dictionary:

```
if len(sys.argv) == 2 and sys.argv[1] == '--list':
  print json.dumps(groups)
else:
  sys.stderr.write('Unsupported command-line:\n%s' % ' '.join(sys.argv))
```

As a further reading, you could cosider an article on developing dynamic inventory sources from [the official Ansible documentation](http://docs.ansible.com/ansible/developing_inventory.html).

----

Tested against `Ansible 1.9.4`, `Python 2.7.8`