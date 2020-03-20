# How to contribute

Contributions are welcome.

Please file bug reports and feature requests to https://github.com/metosin/muuntaja/issues.

## Making changes

* Fork the repository on Github
* Create a topic branch from where you want to base your work (usually the master branch)
* Check the formatting rules from existing code (no trailing whitepace, mostly default indentation)
* Ensure any new code is well-tested, and if possible, any issue fixed is covered by one or more new tests
* Verify that all tests pass using ```lein test```
* Push your code to your fork of the repository
* Make a Pull Request

Installing jars and changing of version numbers can be done with the following scripts:

```sh
./scripts/set-version 1.0.0
./scripts/lein-modules install
```


## Commit messages

1. Separate subject from body with a blank line
2. Limit the subject line to 50 characters
3. Capitalize the subject line
4. Do not end the subject line with a period
5. Use the imperative mood in the subject line
    - "Add x", "Fix y", "Support z", "Remove x"
6. Wrap the body at 72 characters
7. Use the body to explain what and why vs. how

For comprehensive explanation read this [post by Chris Beams](http://chris.beams.io/posts/git-commit/#seven-rules).
