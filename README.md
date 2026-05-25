# Patient Management

A learning-focused microservices project built with **Java** and **PostgreSQL** to understand how a real-world distributed system works using multiple microservices, containerization, asynchronous communication, and cloud deployment concepts.

## Overview

`patient-management` is a personal learning project created to explore and understand how a **multi-microservices architecture** operates in practice.

The project focuses on experimenting with:

- Service-to-service communication using **gRPC**
- Event-driven communication using **Apache Kafka**
- Containerization with **Docker**
- Cloud deployment simulation using **LocalStack**
- Task orchestration using **AWS ECS**
- Database management with **PostgreSQL**

The main goal of this project is not only to build features but also to deeply understand the workflow and interaction between distributed services in a microservices environment.

## Tech Stack

### Backend
- Java
- Spring Boot
- PostgreSQL

### Microservices & Communication
- **gRPC** for synchronous service-to-service communication
- **Apache Kafka** for asynchronous event-driven communication between services

### Infrastructure & Deployment
- **Docker** for containerizing services and managing images
- **LocalStack** to simulate AWS services locally
- **AWS ECS (Elastic Container Service)** for running tasks of individual microservices

## Architecture Goal

This project aims to simulate a system where:

- A request can be sent to **multiple microservices** through **gRPC**
- Multiple services can communicate asynchronously using **Kafka events**
- Each microservice runs independently inside containers
- Services can be deployed and managed similarly to a cloud environment using **LocalStack + AWS ECS**

## Learning Objectives

Through this project, the goal is to understand:

- How a **multi-microservices system** is structured
- How **microservices communicate** synchronously and asynchronously
- How **Docker images and containers** work in a distributed environment
- How **AWS ECS tasks** manage individual services
- How cloud deployment concepts work through **LocalStack**
- Basic infrastructure orchestration and service scaling concepts

## Current Status

This project is still under active learning and experimentation.

Due to some limitations (for example, missing features available only in **LocalStack Pro** and infrastructure differences compared to production AWS), the system is **not yet running 100% exactly like the YouTube demo/tutorial reference**.

However, the project has successfully helped build a solid understanding of:

- Microservices architecture
- Docker-based deployment
- gRPC communication
- Kafka event-driven messaging
- AWS ECS task execution flow
- Local cloud simulation with LocalStack

## Disclaimer

This repository is primarily built for **learning and experimentation purposes**. The codebase may contain unfinished features, experimental implementations, or architectural changes as understanding improves over time.

## Future Improvements

- Improve ECS task orchestration
- Complete LocalStack deployment flow
- Add better monitoring/logging
- Enhance service communication reliability
- Expand microservices functionality
- Optimize deployment architecture
