# Presto Function Guidelines

Presto includes a large library of built-in, core SQL functions such as AVG and CONCAT that are available in every deployment.

Presto also includes extension functions that are not enabled by default but can be optionally enabled in particular deployments.

Finally, Presto allows users to write user defined functions (UDFs) in either Java or SQL that are not stored in this GitHub repository. This is done through the [Plugin SPI](https://prestodb.io/docs/current/develop/functions.html).

This document lays out some guidelines for which category is right for which functions. 

Functions defined in [ANSI SQL](https://jakewheat.github.io/sql-overview/) should be written as core functions in this repository.

Non-standard functions that are commonly available in multiple other SQL dialects such as MySQL and Postgres should also be written as core functions in this library.

Core functions should be broadly applicable to many users in different industries.

Core functions should be purely computational. That is, they do not make network connections or perform I/O outside the database. (Date/time functions like NOW() are an exception.) For example, a function that talks to a remote server to check the current  temperature or a stock price should not be a core function. This should be a user defined function maintained outside the Presto GitHub repository. 

Functions that alias or closely resemble existing core functions should not be included in this repository. For instance, because Presto already has an AVG function it should not also have a MEAN or an AVERAGE function, even if the semantics or arguments are slightly different.

Functions that include trademarked names should be UDFs outside this repository.