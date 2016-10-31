# SPARQL Unlimited

Execute paged [SPARQL updates](https://www.w3.org/TR/sparql11-update) from [Mustache](https://mustache.github.io) templates.

## Usage

Compile using [Leiningen](http://leiningen.org):

```sh
lein uberjar
```

Run from command-line:

```sh
java -jar sparql_unlimited.jar --help
```

You will need to provide a configuration file and a SPARQL update. The configuration in [EDN](https://github.com/edn-format/edn) is a map with the following keys:

* `sparql-endpoint`: URL of the SPARQL 1.1 Update endpoint, such as `http://localhost:8890/sparql-auth`.
* `username`: Name of a user authorized to run SPARQL updates, such as `dba`.
* `password`: Password of the user, such as `dba`.
* `page-size` (optional, default = 10000): Page size of the number of bindings to process in one update.
* `timeout` (optional, default = 3600000): Maximum execution time of the SPARQL operaration in milliseconds.
* `max-attempts` (optional, default = 5): Maximum number of attempts of running the update if timeout occurs until an exception is thrown.
* `default-graph` (optional): IRI of the default graph to update.
* `params` (optional): Map of parameters to use in rendering Mustache template of the SPARQL update.
* `start-from` (optional): An offset to start from when restarting a paged update.

The SPARQL update can either be a SPARQL 1.1 Update file or a Mustache template that can render SPARQL 1.1 Update. If an update is to be executed in pages, it needs to be provided in a Mustache template that uses the parameters `limit` to determine the size of the page to be updated. If an update does not filter out the already processed bindings in its `WHERE` clause, it must filter these bindings by using an `ORDER BY` and an increasing OFFSET using the `offset` variable.

## Issues

The tool can be used only with [Virtuoso](https://github.com/openlink/virtuoso-opensource). Since there is no standard stopping condition for paged SPARQL updates, the tool uses a Virtuoso-specific one. The stopping condition detects if Virtuoso reports 0 modified triples, so that it can be only used for updates that converge to 0 triples.

## License

Copyright &copy; 2015-2016 Jindřich Mynarz

Distributed under the Eclipse Public License version 1.0.
