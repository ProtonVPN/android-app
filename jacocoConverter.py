#!/usr/bin/env python

# Copyright (C) 2013 Rico Huijbers
#
# Permission is hereby granted, free of charge, to any person obtaining a copy of
# this software and associated documentation files (the "Software"), to deal in
# the Software without restriction, including without limitation the rights to
# use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
# of the Software, and to permit persons to whom the Software is furnished to do
# so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#
# Source: https://github.com/rix0rrr/cover2cover

import sys
import defusedxml.ElementTree as ET
import re
import os.path
# defusedxml doesn't define these non-parsing related objects
from xml.etree.ElementTree import Element # nosemgrep
from xml.etree.ElementTree import SubElement # nosemgrep
from xml.etree.ElementTree import tostring # nosemgrep

# branch-rate="0.0" complexity="0.0" line-rate="1.0"
# branch="true" hits="1" number="86"

def find_lines(j_package, filename):
    """Return all <line> elements for a given source file in a package."""
    lines = list()
    sourcefiles = j_package.findall("sourcefile")
    for sourcefile in sourcefiles:
        if sourcefile.attrib.get("name") == os.path.basename(filename):
            lines = lines + sourcefile.findall("line")
    return lines

def line_is_after(jm, start_line):
    return int(jm.attrib.get('line', 0)) > start_line

def method_lines(jmethod, jmethods, jlines):
    """Filter the lines from the given set of jlines that apply to the given jmethod."""
    start_line = int(jmethod.attrib.get('line', 0))
    larger     = list(int(jm.attrib.get('line', 0)) for jm in jmethods if line_is_after(jm, start_line))
    end_line   = min(larger) if len(larger) else 99999999

    for jline in jlines:
        if start_line <= int(jline.attrib['nr']) < end_line:
            yield jline

def convert_lines(j_lines, into):
    """Convert the JaCoCo <line> elements into Cobertura <line> elements, add them under the given element."""
    c_lines = SubElement(into, 'lines')
    for jline in j_lines:
        mb = int(jline.attrib['mb'])
        cb = int(jline.attrib['cb'])
        ci = int(jline.attrib['ci'])

        cline = SubElement(c_lines, 'line')
        cline.set('number', jline.attrib['nr'])
        cline.set('hits', '1' if ci > 0 else '0') # Probably not true but no way to know from JaCoCo XML file

        if mb + cb > 0:
            percentage = str(int(100 * (float(cb) / (float(cb) + float(mb))))) + '%'
            cline.set('branch',             'true')
            cline.set('condition-coverage', percentage + ' (' + str(cb) + '/' + str(cb + mb) + ')')

            cond = SubElement(SubElement(cline, 'conditions'), 'condition')
            cond.set('number',   '0')
            cond.set('type',     'jump')
            cond.set('coverage', percentage)
        else:
            cline.set('branch', 'false')

def guess_filename(path_to_class):
    m = re.match('([^$]*)', path_to_class)
    return (m.group(1) if m else path_to_class) + '.kt'

def add_counters(source, target):
    target.set('line-rate',   counter(source, 'LINE'))
    target.set('branch-rate', counter(source, 'BRANCH'))
    target.set('complexity', counter(source, 'COMPLEXITY', sum))

def fraction(covered, missed):
    return covered / (covered + missed)

def sum(covered, missed):
    return covered + missed

def counter(source, type, operation=fraction):
    cs = source.findall('counter')
    c = next((ct for ct in cs if ct.attrib.get('type') == type), None)

    if c is not None:
        covered = float(c.attrib['covered'])
        missed  = float(c.attrib['missed'])

        return str(operation(covered, missed))
    else:
        return '0.0'

def convert_method(j_method, j_lines):
    c_method = Element('method')
    c_method.set('name',      j_method.attrib['name'])
    c_method.set('signature', j_method.attrib['desc'])

    add_counters(j_method, c_method)
    convert_lines(j_lines, c_method)

    return c_method

def convert_class(j_class, j_package, source_root):
    c_class = Element('class')
    c_class.set('name',     j_class.attrib['name'].replace('/', '.'))
    c_class.set('filename', source_root + '/' + guess_filename(j_class.attrib['name']))

    all_j_lines = list(find_lines(j_package, c_class.attrib['filename']))

    c_methods   = SubElement(c_class, 'methods')
    all_j_methods = list(j_class.findall('method'))
    for j_method in all_j_methods:
        j_method_lines = method_lines(j_method, all_j_methods, all_j_lines)
        c_methods.append(convert_method(j_method, j_method_lines))

    add_counters(j_class, c_class)
    convert_lines(all_j_lines, c_class)

    return c_class

def convert_package(j_package, source_root):
    c_package = Element('package')
    c_package.attrib['name'] = j_package.attrib['name'].replace('/', '.')

    c_classes = SubElement(c_package, 'classes')
    for j_class in j_package.findall('class'):
        c_classes.append(convert_class(j_class, j_package, source_root))

    add_counters(j_package, c_package)

    return c_package

def convert_root(source, target, source_root):
    target.set('timestamp', str(int(source.find('sessioninfo').attrib['start']) / 1000))

    packages = SubElement(target, 'packages')

    for group in source.findall('group'):
        for package in group.findall('package'):
            packages.append(convert_package(package, source_root))

    for package in source.findall('package'):
        packages.append(convert_package(package, source_root))

    add_counters(source, target)

def jacoco2cobertura(filename, source_root):
    if filename == '-':
        root = ET.fromstring(sys.stdin.read())
    else:
        tree = ET.parse(filename)
        root = tree.getroot()

    into = Element('coverage')
    convert_root(root, into, source_root)
    print ('<?xml version="1.0" ?>')
    print (tostring(into, encoding='unicode'))

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print ("Usage: jacocoConverter.py FILENAME [SOURCE_ROOT]")
        sys.exit(1)

    filename    = sys.argv[1]
    source_root = sys.argv[2] if 2 < len(sys.argv) else '.'

    jacoco2cobertura(filename, source_root)
