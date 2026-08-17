const path = require('path');

module.exports = {
  project: {
    android: {
      // AD Glasses embeds RN into this existing Gradle root instead of ./android.
      sourceDir: path.resolve(__dirname),
      appName: 'app',
      // The manifest intentionally relies on the Gradle namespace/applicationId, so give the
      // community CLI the package explicitly instead of asking it to infer one from a manifest
      // package attribute that does not exist.
      packageName: 'com.fersaiyan.cyanbridge',
    },
  },
};
