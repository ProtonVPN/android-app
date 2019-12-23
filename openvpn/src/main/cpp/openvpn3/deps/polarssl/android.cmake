# this one is important
SET(CMAKE_SYSTEM_NAME Linux)
#this one not so much
SET(CMAKE_SYSTEM_VERSION 1)

# specify the cross compiler (assumes that PATH already points to android toolchain)
SET(CMAKE_C_COMPILER   gcc)
SET(CMAKE_CXX_COMPILER g++)

# where is the target environment 
#SET(CMAKE_FIND_ROOT_PATH  /opt/eldk-2007-01-19/ppc_74xx /home/alex/eldk-ppc74xx-inst)

# search for programs in the build host directories
SET(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
# for libraries and headers in the target directories
SET(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
SET(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
