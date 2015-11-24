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
flat = lambda lst: reduce(lambda l, e: l + flat(e) if isinstance(e, list) else l + [e], lst, [])

# search for a group by filter
grp_search = lambda fltr: ldapSrv.search_s('ou=Resources,dc=example,dc=com', ldap.SCOPE_SUBTREE, '(&(objectclass=group)(%s))' % fltr, ['dn'])

# search for members in LDAP groups and return a nested list of them
def grp_members(gdn):
	return [grp_members(grp[0]) for grp in grp_search('memberOf=%s' % gdn)
		] + ldapSrv.search_s('ou=Users,dc=example,dc=com', ldap.SCOPE_SUBTREE, '(&(objectclass=person)(memberOf=%s))' % gdn, [param['-f']])

grp = grp_search('name=%s' % param['-g'])
if not grp:
	sys.stderr.write("Group '%s' isn't found in LDAP\n" % param['-g'])
	sys.exit(2)

print param['-s'].join(sorted(set(ldap_output(res) for res in flat(grp_members(grp[0][0])) if res)))
