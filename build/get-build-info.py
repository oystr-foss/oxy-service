#!/usr/bin/python3

import gbi
import argparse


parser = argparse.ArgumentParser(description="")
parser.add_argument("--repo",        required=True)
parser.add_argument("--main-branch", required=False, default="master")
parser.add_argument("--info",        required=False, default=False, action="store_true")
parser.add_argument("--label",       required=False, default=False, action="store_true")
parser.add_argument("--with-date",   required=False, default=False, action="store_true")

args = parser.parse_args()
map = vars(args)
info = gbi.BuildInfo(map)

if map["info"]:
    info.print_info()
elif map["label"]:
    print(info.get_label())
else:
    print("No selection")
