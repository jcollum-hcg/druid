## Introduction
Druid can use Cassandra as a deep storage mechanism. Segments and their metadata are stored in Cassandra in two tables:
`index_storage` and `descriptor_storage`.  Underneath the hood, the Cassandra integration leverages Astyanax.  The 
index storage table is a [Chunked Object](https://github.com/Netflix/astyanax/wiki/Chunked-Object-Store) repository. It contains
compressed segments for distribution to historical nodes.  Since segments can be large, the Chunked Object storage allows the integration to multi-thread
the write to Cassandra, and spreads the data across all the nodes in a cluster.  The descriptor storage table is a normal C* table that 
stores the segment metadatak.  

## Schema
Below are the create statements for each:

    CREATE KEYSPACE Druid
      WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };
    
    USE Druid; 

    CREATE TABLE index_storage ( key text, chunk text, value blob, PRIMARY KEY (key, chunk)) WITH COMPACT STORAGE;

    CREATE TABLE descriptor_storage ( key varchar, lastModified timestamp, descriptor varchar, PRIMARY KEY (key) ) WITH COMPACT STORAGE;


## Getting Started
First create the schema above.  (I use a new keyspace called `druid`) 

Then, add the following properties to your properties file (`config/historical/runtime.properties`) to enable a Cassandra 
backend.

    druid.storage.cassandra=true
    druid.storage.cassandra.host=localhost:9160
    druid.storage.cassandra.keyspace=druid

Use the `druid-development@googlegroups.com` mailing list if you have questions,
or feel free to reach out directly: `bone@alumni.brown.edu`.


