opts="--selinux-enabled=false"

if [ -f /etc/redhat-release ]
then
	echo "OPTIONS='$opts'" > /etc/sysconfig/docker
else
	if [ -f /etc/debian_version ]
	then
		echo "DOCKER_OPTS='$opts'" > /etc/default/docker
	else
		echo "Unsupported Linux distribution"
		exit 1
	fi
fi
