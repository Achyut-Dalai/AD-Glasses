module.exports = {
  project: {
    android: {
      // The existing Gradle project is the React Native project root rather than
      // living in a conventional ./android subdirectory.
      sourceDir: './',
      appName: 'app',
    },
  },
};
