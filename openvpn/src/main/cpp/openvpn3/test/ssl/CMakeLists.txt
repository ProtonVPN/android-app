set(TEST_PROTO_NTHREADS 1 CACHE STRING "test/ssl/proto - Number of threads")
set(TEST_PROTO_RENEG 900 CACHE STRING "test/ssl/proto - Renegotiation intervall")
set(TEST_PROTO_ITER 1000000 CACHE STRING "test/ssl/proto - Number of iterations")
set(TEST_PROTO_SITER 1 CACHE STRING "test/ssl/proto - Number of high-level iterations")
option(TEST_PROTO_VERBOSE "test/ssl/proto - Enable verbose logging" OFF)

add_executable(proto proto.cpp)
add_core_dependencies(proto)
if (${TEST_PROTO_VERBOSE})
  set(PROTO_CFLAGS_VERBOSE -DVERBOSE)
endif()
target_compile_definitions(proto PRIVATE
  ${PROTO_CFLAGS_VERBOSE}
  -DN_THREADS=${TEST_PROTO_NTHREADS}
  -DRENEG=${TEST_PROTO_RENEG}
  -DITER=${TEST_PROTO_ITER}
  -DSITER=${TEST_PROTO_SITER}
)

# Copy key/certificate files required to run proto
file(COPY . DESTINATION ${CMAKE_CURRENT_BINARY_DIR}
     FILES_MATCHING PATTERN *.key
                    PATTERN *.crt
                    PATTERN dh.pem
)

if (NOT WIN32)
    # We have a number of private fields that depend on compile flags for manual testing
    target_compile_options(proto PRIVATE -Wno-unused-private-field)
endif ()
