require_relative '../../spec_helper'
require_relative 'shared/to_i'
require_relative 'shared/integer_rounding'
require_relative 'shared/integer_ceil_precision'

describe "Integer#ceil" do
  it_behaves_like :integer_to_i, :ceil
  it_behaves_like :integer_rounding_positive_precision, :ceil

  context "with precision" do
    it_behaves_like :integer_ceil_precision, :Integer

    it "converts the precision with #to_int" do
      precision = mock("-1")
      precision.should_receive(:to_int).and_return(-1)
      1234.ceil(precision).should.eql?(1240)
    end
  end
end
