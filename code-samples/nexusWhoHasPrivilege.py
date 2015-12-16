#!/usr/bin/env python

# print a list of all the users & roles having a given privilege

import sys
import xml.etree.ElementTree as tree

xmlPath = '/opt/webapps/sonatype-work/nexus/conf/security.xml'

if len(sys.argv) != 2 or sys.argv[1] in ('-h', '--help'):
	sys.stderr.write('Usage: %s nameOfPrivilege\n' % sys.argv[0])
	sys.exit(1)

privName = sys.argv[1]
xmlRoot = tree.parse(xmlPath).getroot()

# search for the privilege's id
privs = [priv.find('id').text
	for priv in xmlRoot.findall('./privileges/privilege')
	if priv.find('name').text == privName]
if privs:
	privId = privs[0]
else:
	sys.stderr.write("Privilege named '%s' isn't found\n" % privName)
	sys.exit(1)

# search for roles having this privilege

# get a list of role's privileges
rolesPrivs = lambda role: map(lambda priv: priv.text, role.findall('./privileges/privilege'))

# get role id
roleId = lambda role: role.find('id').text

primRoles = [roleId(role)
	for role in xmlRoot.findall('./roles/role')
	if privId in rolesPrivs(role)]

# get a list of roles having these roles

# flatten a list of lists
def flatten(aList):
    for anElem in aList:
        if hasattr(anElem, '__iter__') and not hasattr(anElem, 'split'):
            for subElem in flatten(anElem):
                yield subElem
        else:
            yield anElem

# get a list of the ids of the elements' roles
getRoles = lambda elem: map(lambda role: role.text, elem.findall('roles/role'))

# get a list of roles having the role with a given name
def whoHasRole(roleName):
	return ((subRole, whoHasRole(roleId(subRole)))
		for subRole in xmlRoot.findall('./roles/role')
		if roleName in getRoles(subRole))

secRoles = map(roleId,
	flatten(map(lambda role: whoHasRole(role), primRoles)))

allRoles = set(primRoles) | set(secRoles)

if allRoles:
	print 'Roles:\n\t' + '\n\t'.join(allRoles)

	# get users belonging to these roles
	users = [user.find('userId').text
	for user in xmlRoot.findall('./userRoleMappings/userRoleMapping')
	if allRoles & set(getRoles(user))]
	
	if users:
		print 'Users:\n\t' + '\n\t'.join(set(users))
