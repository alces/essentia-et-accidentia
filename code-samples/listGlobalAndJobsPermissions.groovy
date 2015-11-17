/**
 * list global permissions and permissions on jobs a given user has
 *
 * required Scripler parameters:
 * USERNAME (String) - login of user under inverstigation
 *
 */

import hudson.model.Hudson

// extract a list permissions for a given user from a given xml fragment
xml2perms = {usr, xml ->
	xml.permission.collect {
		it.text().split(':')
	}.findAll {
		it[1] == USERNAME
	}.collect {
		it[0].replaceAll(/^[a-z.]+/, '')
	}
}

root = Hudson.instance.root

println 'Global: ' + xml2perms(usr,
	new XmlSlurper().parse(new File(root, 'config.xml')).authorizationStrategy).join(', ')

println new File(root, 'jobs').listFiles().findAll {
	it.directory
}.collect {
	new File(it, 'config.xml')
}.findAll {
	it.exists()
}.collect {
	[name: it.parentFile.name,
	perm: xml2perms(usr, new XmlSlurper().parse(it).properties.'hudson.security.AuthorizationMatrixProperty')]
}.findAll {
	it.perm
}.collect {
	"$it.name: ${it.perm.join(', ')}"
}.join('\n')
