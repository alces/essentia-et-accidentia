/*** BEGIN META {
	"name": "Search in the environment variables",
	"comment": "Search in the latest builds' environment variables",
	"parameters": ["envVarName", "envVarValueRegex"],
	"core": "1.300",
	"authors": [
		{name : "Alexey Zalesnyi"}
	]
} END META**/

weekAgo = new GregorianCalendar()
weekAgo.add(Calendar.DAY_OF_MONTH, -7)

hdsn = hudson.model.Hudson.instance

envVars = {bld ->
  bld.environment.findAll {
	it.key == envVarName && it.value =~ envVarValueRegex
  }.collect {
	it.value
  }.join()
}

print hdsn.items.collect {
  it.builds.findAll {
    it.time > weekAgo.time
  }
}.findAll {
	it
}.flatten().collect {
    [url: hdsn.rootUrl + it.url, env: envVars(it)]
}.findAll {
    it.env
}.collect {
    "$it.url: $it.env"
}.join('\n')