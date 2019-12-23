/*
 * Copyright 2013 (c) Andreas Schneider <asn@cynapses.org>
 *                    Jakub Hrozek <jakub.hrozek@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdarg.h>
#include <stddef.h>
#include <setjmp.h>
#include <cmocka.h>
#include <stdio.h>
#include <errno.h>
#include <stdbool.h>
#include <string.h>

#include "chef.h"


/* This is the real chef, just not implemented yet, currently it always
 * returns ENOSYS
 */
int chef_cook(const char *order, char **dish_out)
{
    if (order == NULL || dish_out == NULL) return EINVAL;

    return -ENOSYS;
}

/* Print chef return codes as string */
const char *chef_strerror(int error)
{
    switch (error) {
    case 0:
        return "Success";
    case -1:
        return "Unknown dish";
    case -2:
        return "Not enough ingredients for the dish";
    }

    return "Unknown error!";
}

