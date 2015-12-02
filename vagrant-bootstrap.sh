#!/usr/bin/env bash

### Install Docker

apt-get update
apt-get -y install linux-image-extra-$(uname -r)
sh -c "wget -qO- https://get.docker.io/gpg | apt-key add -"
sh -c "echo deb http://get.docker.io/ubuntu docker main\ > /etc/apt/sources.list.d/docker.list"
apt-get update
apt-get -y install lxc-docker

usermod -a -G docker vagrant

### Install ElasticSearch

wget -qO - https://packages.elastic.co/GPG-KEY-elasticsearch | apt-key add -
echo "deb http://packages.elastic.co/elasticsearch/1.4/debian stable main" | tee -a /etc/apt/sources.list.d/elasticsearch-1.4.list
apt-get update
apt-get -y install openjdk-7-jre-headless elasticsearch

### Install MongoDB

apt-get -y install mongodb

# Once MongoDB is installed, need to edit /etc/mongodb.conf to set bind = 0.0.0.0