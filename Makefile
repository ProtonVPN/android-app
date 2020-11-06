.PHONY: debug-local deploy-local image latest local login-deploy help

# To be able to push the CI image from your computer you must add this file + set some variables
# CI_DEPLOY_USER: your username
# CI_JOB_TOKEN: your PAT for gitlab
# CI_REGISTRY: our registry at proton
-include .env

branch ?= development
NAME_IMAGE ?= $(CI_REGISTRY_IMAGE)
TAG_IMAGE := branch-$(subst /,-,$(branch))
MAIN_BRANCH_FOR_LATEST ?= development

# We use :latest so we can use somewhere else, but it's the same as branch-master the other one is for CI
ifeq ($(branch), latest)
	TAG_IMAGE=latest
endif

## Create an image NAME_IMAGE:branch-<branch>
image:
	docker build -t $(NAME_IMAGE):$(TAG_IMAGE) .
	docker tag $(NAME_IMAGE):$(TAG_IMAGE) $(NAME_IMAGE):$(TAG_IMAGE)
	docker push $(NAME_IMAGE):$(TAG_IMAGE)

# Tag the image branch-MAIN_BRANCH_FOR_LATEST (development) as :latest
latest:
	docker pull $(NAME_IMAGE):branch-$(MAIN_BRANCH_FOR_LATEST)
	docker tag $(NAME_IMAGE):branch-$(MAIN_BRANCH_FOR_LATEST) $(NAME_IMAGE):latest
	docker push $(NAME_IMAGE):latest

## For the dev ~ Build the image on your computer: output ci-android:latest
local:
	@ docker build -t "$(NAME_IMAGE)" .
local: NAME_IMAGE = ci-android:latest

## Run the image built via make local on your compute so you can inspect its content
debug-local:
	docker run -it --user=root --network=host $(NAME_IMAGE) bash
debug-local: NAME_IMAGE = ci-android:latest

## Deploy the image of the CI from your computer
deploy-local: login-deploy image

# If you want to deploy an image to our registry you will need to set these variables inside .env
login-deploy:
	docker login -u "$(CI_DEPLOY_USER)" -p "$(CI_JOB_TOKEN)" "$(CI_REGISTRY)"

