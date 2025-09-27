#! /usr/bin/python3

import os
import sys
import datetime

PACKAGE_JSON_PATH = './package.json'
BUILD_GRADLE_PATH = './android/build.gradle'

version = None
with open(PACKAGE_JSON_PATH,'r') as read_handle:
    for line in read_handle.readlines():
        if 'version' in line:
            version = line.split(':')[1].split(',')[0].strip()

if len(sys.argv) < 2:
    print(f"Pass a new version. Current version is {version.replace('"','')}")
    sys.exit(1)

if sys.argv[1] == 'read':
    print(version.replace('"',''),end='')
    sys.exit(0)

build_date = datetime.datetime.now().strftime('%B %d, %Y')
build_version = sys.argv[1]

def update_info(
    input_path:str,
    version_needle:str=None,
    version_replacement:str=None,
    build_needle:str=None,
    build_replacement:str=None
    ):
    print(f"Updating {input_path}")
    file_content = ''
    with open(input_path,'r') as read_handle:
        for line in read_handle.readlines():
            if version_needle and version_needle in line:
                file_content += version_replacement
            elif build_needle and build_needle in line:
                file_content += build_replacement
            else:
                file_content += line
    with open(input_path,'w') as write_handle:
        write_handle.write(file_content)


update_info(
    input_path=PACKAGE_JSON_PATH,
    version_needle='"version"',
    version_replacement=f'    "version": "{build_version}",\n'
)

update_info(
    input_path=BUILD_GRADLE_PATH,
    version_needle='version = ',
    version_replacement=f"version = '{build_version}'"
)


update_info(
    input_path=BUILD_GRADLE_PATH,
    version_needle='versionName',
    version_replacement=f'        versionName "{build_version}"\n'
)