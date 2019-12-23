/*
 * Copyright 2014 Luis Pabon, Jr.
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

/*
 * Programming by Contract is a programming methodology
 * which binds the caller and the function called to a
 * contract. The contract is represented using Hoare Triple:
 *      {P} C {Q}
 * where {P} is the precondition before executing command C,
 * and {Q} is the postcondition.
 *
 * See also:
 * http://en.wikipedia.org/wiki/Design_by_contract
 * http://en.wikipedia.org/wiki/Hoare_logic
 * http://dlang.org/dbc.html
 */
#ifndef CMOCKA_PBC_H_
#define CMOCKA_PBC_H_

#if defined(UNIT_TESTING) || defined (DEBUG)

#include <assert.h>

/*
 * Checks caller responsibility against contract
 */
#define REQUIRE(cond) assert(cond)

/*
 * Checks function reponsability against contract.
 */
#define ENSURE(cond) assert(cond)

/*
 * While REQUIRE and ENSURE apply to functions, INVARIANT
 * applies to classes/structs.  It ensures that intances
 * of the class/struct are consistent. In other words,
 * that the instance has not been corrupted.
 */
#define INVARIANT(invariant_fnc) do{ (invariant_fnc) } while (0);

#else
#define REQUIRE(cond) do { } while (0);
#define ENSURE(cond) do { } while (0);
#define INVARIANT(invariant_fnc) do{ } while (0);

#endif /* defined(UNIT_TESTING) || defined (DEBUG) */
#endif /* CMOCKA_PBC_H_ */

