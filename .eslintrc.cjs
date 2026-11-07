module.exports = {
    env: {
        browser: true,
        node: true,
        es2021: true
    },
    ignorePatterns: ["node_modules/", "platforms/", "demo/", "dist/"],
    extends: ["eslint:recommended"],
    parserOptions: {
        ecmaVersion: 2021,
        sourceType: "module"
    },
    rules: {
        "no-console": "off",
        "no-unused-vars": ["warn", { "argsIgnorePattern": "^_" }],
        "no-undef": "error",
        "eqeqeq": ["error", "always"],
        "curly": ["error", "multi-line"],
        "semi": ["error", "always"],
        "indent": ["error", 4, { "SwitchCase": 1 }],
        "quotes": ["error", "single", { "avoidEscape": true }]
    },
    overrides: [
        {
            files: ["www/**/*.js"],
            env: { browser: true }
        },
        {
            files: ["test/**/*.js", "**/*.spec.js", "**/*.test.js", "**/__tests__/**"],
            env: { jest: true, node: true }
        }
    ]
};
