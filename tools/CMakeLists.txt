cmake_minimum_required (VERSION 2.8.11)
project (preparedb)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_subdirectory(../dlib dlib_build)

#add_subdirectory(../sqlite sqlite_build)

set(SQLITE_DIR ../sqlite)
include_directories(${SQLITE_DIR})
#add_library(sqlite3 SHARED ../sqlite/sqlite3.c)
add_library(sqlite3 ../sqlite/sqlite3.c)

add_executable (preprocess preprocessing.cpp)
target_link_libraries(preprocess dlib::dlib)

add_executable (dump dumpdb.cpp sqlite_orm.h)
target_link_libraries(dump dlib::dlib sqlite3)