#!/bin/bash
sed -i "s/salt=.*/salt={{.Values.matomo.salt}}/g" /bitnami/matomo/config/config.ini.php
