---
layout: post
title: 'Getting List of Hosts from an External Source in Fabric'
date: 2015-11-19 09:56
comments: true
categories: [Python, ActiveDirectory, fabric, spacewalk]
---
![](http://uploads6.wikiart.org/images/gustave-dore/don-quixote-93.jpg)

It looks like Fabric is the most helpful tool I've learnt about in the last 2-3 years, because it has given me possibility to automate the most boring part of my daily work without installing or reconfiguring anything on a lot of servers most of which aren't governed by me. But there's no tool in the world capable of doing all your job for you. And Fabric also has its own shortcomings. One of them is that all the target hosts must be named in a command line or hard-coded in **fabfile.py** (through either `env.hosts` or `env.roledefs`.) In a modern enterprise enviroment, where every sysadmin works with ever changing - and ever growing - pool of servers, it can become a boring task by itself. So, let's try teaching Fabric how to get a list of target hosts for its tasks from some external source. A general idea is that servers used for a similar purposes should have somewhat similar names.

## Let's go for a list of hosts to Active Directory

If a some kind of solution built upon Samba (`winbind/sssd`) is used in your enviromnment to authorize servers' users, then hostnames of your servers must be known to Active Directory, so your Fabric script can get them from there. In order to do so we'll use Python `ldap` module which isn't a part of Python Standard Library (on RedHat-based systems it can be installed through `yum install python-ldap`). The function below looks for hosts with a given name in AD:

```
import ldap

def hosts_list(host_name):
	ldap_srv = ldap.initialize('ldap://dc.example.com')
	ldap_srv.simple_bind_s('binduser@example.com', 'ver7s3ctertw0rd')
	return ldap_srv.search_s('ou=computers,dc=example,dc=com',
		ldap.SCOPE_SUBTREE,
		'(&(objectclass=computer)(name=%s))' % host_name,
		['name'])
```
 
Search in Active Directory understands simple wildcards, so it's possible to get a list of all our git servers by writing something like this: `hosts_list('git*')`

Format of a record returned by `search_s` method is quite tricky: it's a list of a Distinguished Name as its first element and a dict of requested values as the second. Each value in this dict is a list itself. Ergo, to actually convert a result of running `hosts_list` fuction to something Fabric could treat as a correct list of hosts, you have to tweak output of `hosts_list` e.g. that way:

```
env.hosts = map(lambda v: v[1]['name'][0], hosts_list('git*'))
```

## Let's go for a list of hosts to Spacewalk

[Spacewalk](http://www.spacewalkproject.org/) by itself know nothing about filtering list of hosts, so the only way to make request will be to get from our spacewalk server a list of all its known hosts and filter the list on a client side. In this case our function become a bit more complex, but, instead of using quite primitive wildcards, we can now employ full-blown regular expressions (or every other filtering method we would choose.) Spacewalk API works over XML-RPC, which is supported by the Standard Python Library, so no additional Python modules are needed.

```
import xmlrpclib

def hosts_list():
	spw = xmlrpclib.Server('https://spacewalk.example.com/rpc/api', verbose = 0)
	ky = spw.auth.login('admin', 'aVeryS3cretWord')
	return spw.system.list_systems(ky)
```

This function returns a list of dicts, so if we want to filter it by hostname, we can do so this way:

```
import re

find_hosts = lamdba(rgx): filter(lambda nam: re.search(rgx, nam),
  	map(lambda hst: hst['name'], hosts_list()))
    
env.roledefs = {
	'git': find_hosts(r'^git'),
	'test': find_hosts(r'-test\.'),
	# and other roles go here...
}
```

Full vendor's documentation on Spacewalk API can be found on [this pretty ugly-looking site](http://www.spacewalkproject.org/documentation/api/).