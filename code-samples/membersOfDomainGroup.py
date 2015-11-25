#!/usr/bin/env python

# print a list of members of a domain group

param = {
	'-f':	'mail', # field name
	'-s':	'\n', # separator
}

import getopt
import ldap
import re
import sys

try:
	param.update(dict(getopt.getopt(sys.argv[1:], 'g:f:s:')[0]))
	if '-g' not in param:
		sys.stderr.write("-g parameter is required\n")
		sys.exit(1)
except getopt.GetoptError:
	sys.stderr.write("Usage: %s -g groupName [ -f LDAP field ] [ -s output separator ]\n" % sys.argv[0])
	sys.exit(1)

ldapSrv = ldap.initialize('ldap://dc.example.com')
ldapSrv.bind_s('bind-user@example.com', 'bindPasSw0rd')

# get output filed from ldap results
ldap_output = lambda r: r[1][param['-f']][0]

# make a flat list from a list of lists
def flatten(aTree):
	# add next element to a list
	def add_next(aList, anElem):
		if isinstance(anElem, list):
			return aList + flatten(anElem)
		else:
			return aList + [anElem]
	return reduce(add_next, aTree, [])

# search for a group by filter
grp_search = lambda fltr: ldapSrv.search_s('ou=Resources,dc=example,dc=com', ldap.SCOPE_SUBTREE, '(&(objectclass=group)(%s))' % fltr, ['dn'])

# search for users inside a given group
usr_search = lambda grpDN: ldapSrv.search_s('ou=Users,dc=example,dc=com', ldap.SCOPE_SUBTREE, '(&(objectclass=person)(memberOf=%s))' % grpDN, [param['-f']])

# get a nested list of the members of a group with a given DN
def grp_members(grpDN):
	childGroups = grp_search('memberOf=%s' % grpDN)
	childUsers = usr_search(grpDN)
	return [grp_members(grp[0]) for grp in childGroups] + childUsers

grp = grp_search('name=%s' % param['-g'])
if not grp:
	sys.stderr.write("Group '%s' isn't found in LDAP\n" % param['-g'])
	sys.exit(2)

print param['-s'].join(sorted(set(ldap_output(res) for res in flatten(grp_members(grp[0][0])) if res)))
