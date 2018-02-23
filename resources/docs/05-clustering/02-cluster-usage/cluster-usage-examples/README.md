# A Simple Cluster Example

## Run sample in same process
To run this sample, type
```bash
sbt "runMain sample.cluster.simple.SimpleClusterApp"
```
## Run sample in separate processes
It can be more interesting to run them in separate processes. Stop the application and then open three terminal windows.

In the first terminal window, start the first seed node with the following command:
```bash
sbt "runMain sample.cluster.simple.SimpleClusterApp 2551"
```

In the second terminal window, start the second seed node with the following command:
```bash
sbt "runMain sample.cluster.simple.SimpleClusterApp 2552"
```

Start another node in the third terminal window with the following command:
```bash
sbt "runMain sample.cluster.simple.SimpleClusterApp 0"
```

# Worker Dial-in Example

## Run the following commands in separate terminal windows.
```bash
sbt "runMain sample.cluster.transformation.TransformationFrontend 2551"

sbt "runMain sample.cluster.transformation.TransformationBackend 2552"

sbt "runMain sample.cluster.transformation.TransformationBackend 0"

sbt "runMain sample.cluster.transformation.TransformationBackend 0"

sbt "runMain sample.cluster.transformation.TransformationFrontend 0"
```

# Router Example with Group of Routees

## Run the following commands in separate terminal windows.
```bash
sbt "runMain sample.cluster.stats.StatsSample 2551"

sbt "runMain sample.cluster.stats.StatsSample 2552"

sbt "runMain sample.cluster.stats.StatsSampleClient"

sbt "runMain sample.cluster.stats.StatsSample 0"
```
