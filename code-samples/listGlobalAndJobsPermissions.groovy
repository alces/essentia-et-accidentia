/*** BEGIN META {
	"name": "List users' permissions",
	"comment": "List global permissions and permissions on jobs a given user has",
	"parameters": ["USERNAME"],
	"core": "1.300",
	"authors": [
		{name : "Alexey Zalesnyi"}
	]
} END META**/

import hudson.model.Hudson

// extract a list permissions for a given user from a given xml fragment
xml2perms = {usr, xml ->
	xml.permission.collect {
		it.text().split(':')
	}.findAll {
		it[1] == usr
	}.collect {
		it[0].replaceAll(/^[a-z.]+/, '')
	}
}

root = Hudson.instance.root
slrp = new XmlSlurper()

println 'Global: ' + xml2perms(USERNAME,
	slrp.parse(new File(root, 'config.xml')).authorizationStrategy).join(', ')

println new File(root, 'jobs').listFiles().findAll {
	it.directory
}.collect {
	new File(it, 'config.xml')
}.findAll {
	it.exists()
}.collect {
	[name: it.parentFile.name,
	perm: xml2perms(USERNAME,
		slrp.parse(it).properties.'hudson.security.AuthorizationMatrixProperty')]
}.findAll {
	it.perm
}.collect {
	"$it.name: ${it.perm.join(', ')}"
}.join('\n')
