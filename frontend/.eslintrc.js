module.exports = {
  env: {
    browser: true,
    es6: true,
    jasmine: true,
  },
  globals: {
    Highcharts: false,
  },
  extends: ['eslint:recommended', 'plugin:react/recommended'],
  parserOptions: {
    ecmaFeatures: {
      jsx: true,
    },
  },
  plugins: ['react', 'react-hooks'],
  settings: {
    react: {
      createClass: 'createReactClass', // Regex for Component Factory to use,
      // default to "createReactClass"
      pragma: 'React', // Pragma to use, default to "React"
      version: 'detect', // React version. "detect" automatically picks the version you have installed.
      // You can also use `16.0`, `16.3`, etc, if you want to override the detected value.
      // default to latest and warns if missing
      // It will default to "detect" in the future
      flowVersion: '0.53', // Flow version
    },
    propWrapperFunctions: [
      // The names of any function used to wrap propTypes, e.g. `forbidExtraProps`. If this isn't set, any propTypes wrapped in a function will be skipped.
      'forbidExtraProps',
      { property: 'freeze', object: 'Object' },
      { property: 'myFavoriteWrapper' },
    ],
    linkComponents: [
      // Components used as alternatives to <a> for linking, eg. <Link to={ url } />
      'Hyperlink',
      { name: 'Link', linkAttribute: 'to' },
    ],
  },
  rules: {
    'react-hooks/rules-of-hooks': 'error',
    'react-hooks/exhaustive-deps': 'warn',
    'react/jsx-uses-react': 'error',
    'react/jsx-uses-vars': 'error',
    'array-callback-return': 'error',
    'arrow-body-style': 'off',
    eqeqeq: ['error', 'always'],
    // Comments sometimes begin with code, which may not be capitalised. Therefore commenting-out this unreasonable rule.
    //"capitalized-comments":         ["error", "always", {"ignoreInlineComments": true, "ignoreConsecutiveComments": true}],
    'comma-dangle': ['error', 'always-multiline'],
    'comma-spacing': ['error', { before: false, after: true }],
    'comma-style': 'error',
    camelcase: 'error',
    'consistent-this': ['error', 'me'],
    curly: 'error',
    'default-case': 'error',
    'implicit-arrow-linebreak': 'error',
    'key-spacing': 'error',
    'keyword-spacing': 'error',
    'linebreak-style': ['error', 'unix'],
    'no-console': 'off',
    'no-debugger': 'error',
    'no-dupe-args': 'error',
    'no-dupe-keys': 'error',
    'no-empty': 'error',
    'no-eq-null': 'error',
    'no-eval': 'error',
    'no-extra-semi': 'error',
    'no-irregular-whitespace': 'error',
    'no-mixed-spaces-and-tabs': 'error',
    'no-trailing-spaces': 'error',
    'no-unreachable': 'error',
    'no-var': 'error',
    'one-var-declaration-per-line': 'error',
    'spaced-comment': 'error',
    'valid-typeof': 'error',
  },
};
