//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2019-2020 OpenVPN Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.


// We have two set of measurements for these tests
//
// 1. A coarse measurement based on time()
//    These are tracked in 'chk_start' and 'chk_end'
//
// 2. A fine grained measurement from cpu_time()
//    These are tracked in 'start' and 'end'
//
// We calculate the difference before and after a
// a workload has run, to measure how long it ran.
// This is done for both measurement approaches.
// The runtime is saved in runtime and chk_runtime
//
// To pass this test, absolute difference between
// runtime and chk_runtime must be less than 1 second.
//

//#define DEBUG  // Define this macro to get more details

#include "test_common.h"
#include <cstdint>
#include <unistd.h>
#include <memory>
#include <mutex>
#include <openvpn/time/cputime.hpp>
#include <algorithm>

#ifdef DEBUG
#define DEBUG_DUMP(msg, st, en, rt, chst, chen, chrt, md)             \
  std::cout << msg << std::endl                                       \
            << "start = " << std::to_string(st) << std::endl          \
            << "end = " << std::to_string(en) << std::endl            \
            << "runtime = " << std::to_string(rt) << std::endl        \
            << "chk_start = " << std::to_string(chst) << std::endl    \
            << "chk_end = " << std::to_string(chen) << std::endl      \
            << "chk_runtime = " << std::to_string(chrt) << std::endl  \
            << "measurement difference = "                            \
            << std::to_string(md) << std::endl                        \
            << "--------------------------------------" << std::endl

#else
#define DEBUG_DUMP(msg, st, en, rt, chst, chen, chrt, md) {}
#endif

#define MEASURE(v, chkv, thread)          \
  double v = openvpn::cpu_time(thread);   \
  ASSERT_GE(v, 0);                        \
  double chkv = time(NULL)

#define CALCULATE(msg, st, en, rt, chst, chen, chrt, md) \
  double rt = en - st;                                   \
  double chrt = chen - chst;                             \
  double md = std::max(rt, chrt) - std::min(rt, chrt);   \
  DEBUG_DUMP(msg, st, en, rt, chst, chen, chrt, md)


typedef std::shared_ptr<std::thread> ThreadPtr;


// For simplicty, keep the total thread runtime
// in a global variable, protected by a mutex
// on updates
std::mutex update_guard;
double thread_runtime = 0;

void update_thread_runtime(double val)
{
    std::lock_guard<std::mutex> ug(update_guard);
    thread_runtime += val;
}


namespace unittests
{
    static void workload(const uint16_t multiplier)
    {
        // A very simple busy loop workload
        //
        // We can't use sleep() or any similar timing
        // as this does not increase the tracked runtime
        // in the kernel; the process does not really run.
        //

	std::random_device rd;
	std::mt19937 gen(rd()); //Standard mersenne_twister_engine seeded with rd()


        double d=0;
        for (unsigned int i = UINT16_MAX * multiplier; i > 0; i--)
        {
            d += gen();
        }
    }


    TEST(CPUTime, cpu_time_pid)
    {
        // Measure the runtime of the workload
        MEASURE(start, chk_start, false);
        workload(400);
        MEASURE(end, chk_end, false);

        // Calculate runtimes and differences
        CALCULATE("single PID",
                  start, end, runtime,
                  chk_start, chk_end, chk_runtime,
                  measurement_diff);

        ASSERT_LT(measurement_diff, 10);
    }


    void worker_thread(const uint8_t id)
    {
        MEASURE(thr_start, chk_thr_start, true);
        workload(400);
        MEASURE(thr_end, chk_thr_end, true);

        CALCULATE("Worker thread " << std::to_string(id),
                  thr_start, thr_end, thr_runtime,
                  chk_thr_start, chk_thr_end, chk_thr_runtime,
                  thr_measurement_diff);
        update_thread_runtime(thr_runtime);

        // Since the chk_thr_runtime (chk_thr_end - chk_thr_start)
        // is based on epoc seconds of the system, this doesn't
        // give a too good number when running multiple threads.
        //
        // If more threads are running on the same CPU core,
        // one of the threads might be preempted.  The clock time
        // (chk_thr_runtime) will continue to tick, but the real
        // runtime (thr_runtime) will not.  Which will increase
        // the difference between the measured runtimes.
        //
        // The value of 5 is just an educated guess of what
        // we might find acceptable.  This might be too high
        // on an idle system, but too low on a loaded system.
        //
        ASSERT_LT(thr_measurement_diff, 5);
    }


    void run_threads(const uint8_t num_threads)
    {
        std::vector<ThreadPtr> threads;
        for (uint8_t i = 0; i < num_threads; i++)
        {
            ThreadPtr tp;
            tp = std::make_shared<std::thread>([id=i](){ worker_thread(id); });
            threads.push_back(tp);
        }

        for (const auto& t : threads)
        {
            t->join();
        }
    }


    TEST(CPUTime, cpu_time_thread_1)
    {
        // Meassure running a single worker thread
        MEASURE(parent_start, chk_parent_start, false);
        run_threads(1);
        MEASURE(parent_end, chk_parent_end, false);

        CALCULATE("Parent thread - 1 child thread",
                  parent_start, parent_end, runtime,
                  chk_parent_start, chk_parent_end, chk_runtime,
                  parent_diff);

        ASSERT_LT(parent_diff, 10);
    }


    TEST(CPUTime, cpu_time_thread_numcores)
    {
        // Use number of available cores
        auto num_cores = std::min(std::thread::hardware_concurrency(), 1u);

        // Meassure running a single worker thread
        MEASURE(parent_start, chk_parent_start, false);
        run_threads(num_cores);
        MEASURE(parent_end, chk_parent_end, false);

        CALCULATE("Parent thread - " << std::to_string(num_cores) << " child thread",
                  parent_start, parent_end, runtime,
                  chk_parent_start, chk_parent_end, chk_runtime,
                  parent_diff);
#ifdef DEBUG
        std::cout << "Total thread runtime: " << std::to_string(thread_runtime) << std::endl;
#endif

        // The main process (this PID) will have a total runtime
        // which accounts for all runtime of the running threads.
        // We still give a bit extra slack, to reduce the risk of
        // false positives, due to the possibility of premption
        // (see comment in worker_thread() for details).  But
        // the difference should not neccesarily deviate as much
        // here.
        ASSERT_LT(parent_diff, 3 + thread_runtime);
    }
}  // namespace
