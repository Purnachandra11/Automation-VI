const mongoose = require("mongoose");
const ObjectId = require('mongoose').Types.ObjectId;
const registrationSchema = new mongoose.Schema({
    
    email: String,
    password: String,
    original_password : String,
   
    is_deleted:{type:Number,default:0},
    created: { type: Date, default: Date.now },
    modified: { type: Date, default: Date.now }

});
Registration = mongoose.model("users", registrationSchema);

const fileInfoSchema = new mongoose.Schema({
    
    name: String,
    uploaded_date: String,
    result : String,
    is_deleted:{type:Number,default:0},
    created: { type: Date, default: Date.now },
    modified: { type: Date, default: Date.now }

});
FileInfo = mongoose.model("file_info", fileInfoSchema);

 






module.exports = {
    Registration,
    FileInfo

}
