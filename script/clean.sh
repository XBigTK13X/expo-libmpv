#! /bin/bash

rm -rf build
rm -rf android/build
rm -rf node_modules
cd example
rm -rf android/build
rm -rf node_modules
cd ..
npx yarn install
cd example
npx yarn install

EXPO_NO_GIT_STATUS=1 EXPO_TV=1 npx expo prebuild