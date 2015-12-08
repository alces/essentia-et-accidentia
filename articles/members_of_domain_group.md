---
layout: post
title: 'Getting a List of Domain Group's Members Using Python'
date: 2015-12-08 10:38
comments: true
categories: [Python, Ldap, ActiveDirectory]
---
![](https://upload.wikimedia.org/wikipedia/commons/4/47/Poucet7.JPG)

The script described in this article gets a list of the members of a given Active Directory group using Python LDAP interface (`ldap` module doesn't included in the Python Standard Library, but on both the RedHat and Debian derivatives it can be installed as `python-ldap` package from a standard repository.) Nested groups are supported. The script understands 3 command line parameters:

* `-g` (required) name of group to get members from;

* `-a` (optional) LDAP attribute to display in the members' list (default value: **mail**);

* `-s` (optional) a string to separate members' records by (default value: **\n**).

The most boring but necessary part of every command-line program is parsing its parameters:

```
# default values
param = {
	'-a':	'mail', # attribute name
	'-s':	'\n', # separator
}

import getopt
import sys

try:
	param.update(dict(getopt.getopt(sys.argv[1:], 'g:f:s:')[0]))
	if '-g' not in param:
		sys.stderr.write("-g parameter is required\n")
		sys.exit(1)
except getopt.GetoptError:
  sys.stderr.write(
    'Usage: %s -g groupName [ -a LDAP attr ] [ -s output separator ]\n' % 
    sys.argv[0])
  sys.exit(1)
```

Next, let's go to a more interesting business. Connect our Active Directory server and try searching for the desired group by name (because in order to learn its members, we have to learn its DN first):

```
import ldap

ldapSrv = ldap.initialize('ldap://dc.example.com')
ldapSrv.bind_s('bind-user@example.com', 'bindPasSw0rd')

import re

rootGrp = ldapSrv.search_s('ou=Resources,dc=example,dc=com',
		ldap.SCOPE_SUBTREE,
		'(&(objectclass=group)(name=%s))' % param['-g']
		['dn'])
    
if not rootGrp:
	sys.stderr.write("Group '%s' isn't found in LDAP\n" % param['-g'])
	sys.exit(2)
```

Members of a group in LDAP can be not only users but the other groups too, so our task become recursive. Let's write a function recursively reviewing all the nested groups and returning a list of all their users:

```
def grp_members(grpDN):
  filterExpr = '(&(objectclass=%%s)(memberOf=%s))' % grpDN
	childGroups = ldapSrv.search_s('ou=Resources,dc=example,dc=com',
		ldap.SCOPE_SUBTREE,
		filterExpr % 'group',
		['dn'])
	childUsers = ldapSrv.search_s('ou=Users,dc=example,dc=com',
		ldap.SCOPE_SUBTREE,
		filterExpr % 'person',
		[param['-a']])
	return [grp_members(grp[0]) for grp in childGroups] + childUsers
```

As one can see, this function is a bit tricky: if our root group has other groups inside, we'll get a list of the nested lists of unknown depth. Unlike Groovy or Ruby, Python by itself knows nothing about how to make a nested list flat. So, let's write a function doing exactly that:  

```
def flatten(aTree):
	# add next element to a list
	def add_next(aList, anElem):
		if isinstance(anElem, list):
			return aList + flatten(anElem)
		else:
			return aList + [anElem]
	return reduce(add_next, aTree, [])
```

With these two functions under our belt, getting a list of all the members of our group will be as simple as that:

```
members = set(grp_members(grp[0][0]))
```

(we must convert a list returned by `flatten` function to a **set**, because the same user can be a member of more than one group in the tree of nested groups.)

For now, the only thing left is to extract the desired field from users' data, sort the results, and print them out delimited by the choosen delimiter:

```
print param['-s'].join(
  sorted(
    map(lambda usr: usr[1][param['-a']][0], members)
  )
)
```

The full script's source can be found on [my github page](https://github.com/alces/essentia-et-accidentia/blob/master/code-samples/membersOfDomainGroup.py).

----

Tested againts `Python 2.7.8`