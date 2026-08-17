module.exports = {
  project: {
    android: {
      // The React Native workspace is also the existing Android Gradle root. CLI 20 resolves
      // sourceDir relative to the JS project root, so keep this relative instead of feeding it
      // an absolute path that would be joined to the root a second time.
      sourceDir: '.',
      appName: 'app',
      // The manifest intentionally relies on the Gradle namespace/applicationId, so give the
      // community CLI the package explicitly instead of asking it to infer one from a manifest
      // package attribute that does not exist.
      packageName: 'com.fersaiyan.cyanbridge',
    },
  },
};
