/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2018-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.test.embedding.fixtures;

import org.graalvm.polyglot.HostAccess;

@HostAccess.Implementable
public interface FluidForce {

    // Example copied from the JRuby wiki

    String RUBY_SOURCE = "class EthylAlcoholFluidForce\n" +
            "  def getFluidForce(x, y, depth)\n" +
            "    area = Math::PI * x * y\n" +
            "    49.4 * area * depth\n" +
            "  end\n" +
            "end\n" +
            "\n" +
            "EthylAlcoholFluidForce.new";

    double getFluidForce(double a, double b, double depth);

}
