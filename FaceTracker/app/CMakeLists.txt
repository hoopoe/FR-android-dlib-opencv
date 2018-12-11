# Sets the minimum version of CMake required to build the native
# library. You should either keep the default value or only pass a
# value of 3.4.0 or lower.

cmake_minimum_required(VERSION 3.4.1)
#set(CMAKE_VERBOSE_MAKEFILE on)
#set (CMAKE_CXX_STANDARD 11)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)


set(SQLITE_DIR ../../sqlite)
include_directories(${SQLITE_DIR})
add_library(sqlite3 SHARED ../../sqlite/sqlite3.c)

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

set(DLIB_DIR ../../dlib)
include_directories(${DLIB_DIR})

add_library( # Sets the name of the library.
             lib_dlib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             # Associated headers in the same location as their source
             # file are automatically included.
             ../../dlib/dlib/threads/threads_kernel_shared.cpp
             ../../dlib/dlib/entropy_decoder/entropy_decoder_kernel_2.cpp
             ../../dlib/dlib/base64/base64_kernel_1.cpp
             ../../dlib/dlib/threads/threads_kernel_1.cpp
             ../../dlib/dlib/threads/threads_kernel_2.cpp
             ../../dlib/dlib/threads/thread_pool_extension.cpp
             ../../dlib/dlib/threads/async.cpp
             ../../dlib/dlib/cuda/cpu_dlib.cpp
             ../../dlib/dlib/cuda/tensor_tools.cpp
             )

add_library( # Sets the name of the library.
             native-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             # Associated headers in the same location as their source
             # file are automatically included.
             src/main/cpp/native-lib.cpp )

target_link_libraries( # Specifies the target library.
                       native-lib

                       sqlite3

                       lib_dlib

                        -ljnigraphics

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )

add_subdirectory("./jni")
