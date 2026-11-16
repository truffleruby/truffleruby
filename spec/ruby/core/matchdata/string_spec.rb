require_relative '../../spec_helper'

describe "MatchData#string" do
  it "returns a copy of the match string" do
    str = /(.)(.)(\d+)(\d)/.match("THX1138.").string
    str.should == "THX1138."
  end

  it "returns a frozen copy of the match string" do
    str = /(.)(.)(\d+)(\d)/.match("THX1138.").string
    str.should == "THX1138."
    str.should.frozen?
  end

  it "returns the same frozen string for every call" do
    md = /(.)(.)(\d+)(\d)/.match("THX1138.")
    md.string.should.equal?(md.string)
  end

  it "returns the matched string itself if it is already frozen" do
    str = "THX1138.".freeze
    /(.)(.)(\d+)(\d)/.match(str).string.should.equal?(str)
  end

  it "returns a frozen copy if the matched string is not frozen" do
    str = +"THX1138."
    md = /(.)(.)(\d+)(\d)/.match(str)
    md.string.should_not.equal?(str)
    md.string.should.frozen?
    str << "x"
    md.string.should == "THX1138."
  end

  it "returns a frozen copy of the matched string for gsub!(String)" do
    s = +'he[[o'
    s.gsub!('[', ']')
    $~.string.should == 'he[[o'
    $~.string.should.frozen?
  end
end
