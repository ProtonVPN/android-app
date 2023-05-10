# This file is a helper file to generate the test pattern for test_ip.cpp shift tests
import random

allshifts = [0, 1, 31, 32, 33, 45, 63, 64, 67, 80, 97, 127, 128]


def gen_left_shifts(origip):
    for shift in allshifts:
        left_shifted = origip << shift

        # mask with 128bit mask
        left_shifted = left_shifted & (2 ** 128 - 1)
        print_bytes(shift, left_shifted)


def gen_right_shifts(origip):
    for shift in allshifts:
        right_shifted = origip >> shift

        print_bytes(shift, right_shifted)


def print_bytes(shift, ip):
    print("{ %d, " % shift, end='')

    bytes = []
    for _ in range(0, 16):
        bytes.append("0x%x" % (ip & 0xff))
        ip = ip >> 8

    # need the highest byte first
    bytes.reverse()
    print("{ %s }}," % ", ".join(bytes))


if __name__ == '__main__':
    origip = 0x11223344556677889900aabbccddeeff
    randip = random.getrandbits(128)
    gen_left_shifts(origip)
    gen_left_shifts(randip)

    gen_right_shifts(origip)
    gen_right_shifts(randip)
