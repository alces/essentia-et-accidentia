#!/bin/sh

owner=tomcat
timeout=10
export CATALINA_HOME=/opt/tomcat
export CATALINA_OPTS="-server -Xms1024m -Xmx1024m"
export CATALINA_PID=$CATALINA_HOME/logs/tomcat.pid

catalina() {
        if [ "$1" = "stop" ]
        then
                cmd="stop $timeout -force"
        else
                cmd="$1"
        fi
        su -s /bin/sh -c "$CATALINA_HOME/bin/catalina.sh $cmd" $owner
}

status() {
        if [ -f "$CATALINA_PID" ]
        then
                pid=`cat $CATALINA_PID`
                if [ "`ps ho user -p $pid`" = "$owner" ]
                then
                        echo "Running (PID: $pid)"
                        exit 0
                else
                        echo "PID-file exists, but process isn't found"
                fi
        else
                echo "Stopped"
        fi
        exit 1
}

case $1 in
restart)
        catalina stop
        catalina start
        ;;
status)
        status
        ;;
*)
        catalina $1
        ;;
esac
