/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef RUNTIME_GC_STMS_GC_H
#define RUNTIME_GC_STMS_GC_H

#include "SingleThreadMarkAndSweep.hpp"

namespace kotlin {
namespace gc {

using GC = kotlin::gc::SingleThreadMarkAndSweep;

inline constexpr bool kSupportsMultipleMutators = false;

} // namespace gc
} // namespace kotlin

#endif // RUNTIME_GC_STMS_GC_H
