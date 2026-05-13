const mongoose = require("mongoose");

const url = "mongodb://localhost:27017/VI_DB";

mongoose.connect(url)
  .then(() => {
    console.log("✅ Connected to MongoDB");
  })
  .catch((err) => {
    console.error("❌ MongoDB connection error:", err.message);
  });
