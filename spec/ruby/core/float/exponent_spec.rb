require_relative '../../spec_helper'

describe "Float#**" do
  it "returns self raise to the other power" do
    (2.3 ** 3).should be_close(12.167,TOLERANCE)
    (5.2 ** -1).should be_close(0.192307692307692,TOLERANCE)
    (9.5 ** 0.5).should be_close(3.08220700148449, TOLERANCE)
    (9.5 ** 0xffffffff).to_s.should == 'Infinity'
  end

  it "returns 1.0 for 1.0 raised to an infinite or Bignum power and -1.0 raised to an infinite power" do
    (1.0 ** Float::INFINITY).should.eql?(1.0)
    (1.0 ** -Float::INFINITY).should.eql?(1.0)
    (1.0 ** (2**1100)).should.eql?(1.0)
    ((-1.0) ** Float::INFINITY).should.eql?(1.0)
    ((-1.0) ** -Float::INFINITY).should.eql?(1.0)
  end

  it "returns a complex number when negative and raised to a fractional power" do
    ((-8.0) ** (1.0/3))      .should be_close(Complex(1, 1.73205), TOLERANCE)
    ((-8.0) ** Rational(1,3)).should be_close(Complex(1, 1.73205), TOLERANCE)
  end
end
