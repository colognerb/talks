class CreateTagStrings < ActiveRecord::Migration
  def self.up
    create_table :tag_strings do |t|
      t.string :name

      t.timestamps
    end
  end

  def self.down
    drop_table :tag_strings
  end
end
