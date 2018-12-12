cmake_minimum_required (VERSION 2.8.11)
project (preparedb)

add_subdirectory(../dlib dlib_build)

add_executable (preprocess preprocessing.cpp)
target_link_libraries(preprocess dlib::dlib)

add_executable (dump dumpdb.cpp)
target_link_libraries(dump dlib::dlib)