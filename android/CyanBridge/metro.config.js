const {getDefaultConfig, mergeConfig} = require('@react-native/metro-config');

/**
 * Metro lives beside the existing Android project. Keeping the configuration
 * deliberately small makes the brownfield migration easy to reason about.
 */
module.exports = mergeConfig(getDefaultConfig(__dirname), {});
