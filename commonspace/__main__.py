#!/usr/bin/env python
import argparse, os, re

import log
from subprocess import call
from datetime import datetime, timedelta

td_regex = re.compile(r'((?P<hours>\d+?)h)?((?P<minutes>\d+?)m)?((?P<seconds>\d+?)s)?')

def callScala(*args):
    s = './sbt "run'
    for arg in args:
        s += " %s" % (str(arg))
    s += '"'
    print s
    call(s, shell=True)

def latlong_arg(s):
    ll = s.split(',')
    if not len(ll) == 2:
        msg = "%s is not a valid lat long string (e.g. 39.958823,-75.158553)"
        raise argparse.ArgumentTypeError(msg % s)
    lat = float(ll[0])
    lng = float(ll[1])
    if lat < -90 or lat > 90:
        msg = "%f is not a valid latitude"
        raise argparse.ArgumentTypeError(msg % lat)        
    if lat < -90 or lat > 90:
        msg = "%f is not a valid longitude"
        raise argparse.ArgumentTypeError(msg % lng)

    return (lat,lng)

def time_arg(s):
    try:                        
        return datetime.strptime(s, '%H:%M')
    except:
        msg = "%s is not a valid time string (e.g. 15:02)"
        raise argparse.ArgumentTypeError(msg % s)

def duration_arg(s):
    parts = td_regex.match(s)
    if not parts:
        msg = "%s is not a valid time string (e.g. 15m)"
        raise argparse.ArgumentTypeError(msg % s)
    if not parts.group(0):
        msg = "%s is not a valid time string (e.g. 15m)"
        raise argparse.ArgumentTypeError(msg % s)
    parts = parts.groupdict()
    time_params = {}
    for (name, param) in parts.iteritems():
        if param:
            time_params[name] = int(param)
    return timedelta(**time_params)

class NearestCommand:
    @staticmethod
    def execute(args):
        (lat,lng) = args.latlong
        callScala("nearest",lat,lng)

    @staticmethod
    def add_parser(subparsers):
        parser = subparsers.add_parser('nearest')

        parser.add_argument('latlong',
                            metavar='LATLONG',
                            type=latlong_arg,
                            help='Latitude and Longitude to find nearest vertex to  (e.g. 39.958823,-75.158553).')
        parser.set_defaults(func=NearestCommand.execute)

class SptCommand:
    @staticmethod
    def execute(args):
        (lat,lng) = args.latlong
        starttime = args.starttime.hour * 60 * 60 + args.starttime.minute * 60
        duration = args.duration.seconds
        callScala("spt",
                  lat,
                  lng,
                  starttime,
                  duration)

    @staticmethod
    def add_parser(subparsers):
        parser = subparsers.add_parser('spt')

        parser.add_argument('latlong',
                            metavar='LATLONG',
                            type=latlong_arg,
                            help='Latitude and longetude of shortest path tree start point.')

        parser.add_argument('starttime',
                            metavar='STARTTIME',
                            type=time_arg,
                            help='Start time of trip.')

        parser.add_argument('duration',
                            metavar='DURATION',
                            type=duration_arg,
                            help='Maximum duration of trip.')

        parser.set_defaults(func=SptCommand.execute)

class TravelTimeCommand:
    @staticmethod
    def execute(args):
        (slat,slng) = args.startlatlong
        (elat,elng) = args.endlatlong
        starttime = args.starttime.hour * 60 * 60 + args.starttime.minute * 60
        duration = args.duration.seconds
        callScala("traveltime",
                  slat,
                  slng,
                  elat,
                  elng,
                  starttime,
                  duration)

    @staticmethod
    def add_parser(subparsers):
        parser = subparsers.add_parser('traveltime')

        parser.add_argument('startlatlong',
                            metavar='LATLONG',
                            type=latlong_arg,
                            help='Latitude and longetude of the start point.')

        parser.add_argument('endlatlong',
                            metavar='LATLONG',
                            type=latlong_arg,
                            help='Latitude and longetude of the destination point.')

        parser.add_argument('starttime',
                            metavar='STARTTIME',
                            type=time_arg,
                            help='Start time of trip.')

        parser.add_argument('duration',
                            metavar='DURATION',
                            type=duration_arg,
                            help='Maximum duration of trip.')

        parser.set_defaults(func=TravelTimeCommand.execute)

class ListCommand:
    @staticmethod
    def execute(args):
        (slat,slng) = args.startlatlong
        starttime = args.starttime.hour * 60 * 60 + args.starttime.minute * 60
        duration = args.duration.seconds
        callScala("list",
                  args.config,
                  slat,
                  slng,
                  starttime,
                  duration)

    @staticmethod
    def add_parser(subparsers):
        parser = subparsers.add_parser('list')

        parser.add_argument('config',
                            metavar='CONFIG',
                            help='Path to configuration data.')

        parser.add_argument('startlatlong',
                            metavar='LATLONG',
                            type=latlong_arg,
                            help='Latitude and longetude of the start point.')

        parser.add_argument('starttime',
                            metavar='STARTTIME',
                            type=time_arg,
                            help='Start time of trip.')

        parser.add_argument('duration',
                            metavar='DURATION',
                            type=duration_arg,
                            help='Maximum duration of trip.')

        parser.set_defaults(func=ListCommand.execute)

class GetOutgoingCommand:
    @staticmethod
    def execute(args):
        callScala("getoutgoing",args.osmnode)

    @staticmethod
    def add_parser(subparsers):
        parser = subparsers.add_parser('getoutgoing')

        parser.add_argument('osmnode',
                            metavar='NODEID',
                            help='OSM node id.')

        parser.set_defaults(func=GetOutgoingCommand.execute)

class BuildGraphCommand:
    @staticmethod
    def execute(args):
        call('./sbt assembly', shell=True)
        call('java -Xmx10g -jar target/commonspace-assembly-0.1.0-SNAPSHOT.jar buildgraph ' + args.config, shell=True)

    @staticmethod
    def add_parser(subparsers):
        parser = subparsers.add_parser('buildgraph')

        parser.add_argument('config',
                            metavar='CONFIG',
                            help='Path to configuration data.')

        parser.set_defaults(func=BuildGraphCommand.execute)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers()

    NearestCommand.add_parser(subparsers)
    SptCommand.add_parser(subparsers)
    TravelTimeCommand.add_parser(subparsers)
    ListCommand.add_parser(subparsers)
    GetOutgoingCommand.add_parser(subparsers)
    BuildGraphCommand.add_parser(subparsers)

    args = parser.parse_args()
    args.func(args)
