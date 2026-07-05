# Consistent Hashing

## Hash Ring

```mermaid
graph TD
    subgraph Ring ["Hash Ring  ·  0 → 2³²"]
        direction LR
        P0["0"]
        VA1["NodeA-vnode-2<br/>pos: 615M"]
        VB1["NodeB-vnode-2<br/>pos: 230M"]
        VC1["NodeC-vnode-5<br/>pos: 110M"]
        VA2["NodeA-vnode-8<br/>pos: 321M"]
        VB2["NodeB-vnode-0<br/>pos: 1.32B"]
        VC2["NodeC-vnode-0<br/>pos: 725M"]
        VA3["NodeA-vnode-1<br/>pos: 1.56B"]
        PMAX["2³² ≈ 4.29B"]
    end

    P0 --> VC1 --> VA2 --> VB1 --> VA1 --> VC2 --> VB2 --> VA3 --> PMAX
```

## Key Lookup (Clockwise Walk)

```mermaid
flowchart LR
    K["key = 'user123'"]
    H["hash('user123')\n= 1,402,751,844"]
    CE["TreeMap.ceilingEntry\n(1,402,751,844)"]
    OWN["Coordinator =\nNodeB-vnode-0\n→ physical NodeB"]

    K -->|MD5 first 4 bytes| H
    H -->|O log n lookup| CE
    CE -->|extract NodeInfo| OWN
```

## Why Virtual Nodes

```mermaid
xychart-beta
    title "Key Distribution — 10,000 keys"
    x-axis ["NodeA", "NodeB", "NodeC"]
    y-axis "Keys (%)" 0 --> 60
    bar [54.25, 13.75, 32.00]
    line [33.33, 33.33, 33.33]
```

> **Top bars** = 1 virtual node per physical node (terrible balance).
> **Line** = ideal 33.3% target.
> At 150 virtual nodes per physical node, bars converge to the line.