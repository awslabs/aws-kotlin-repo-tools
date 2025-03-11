#!/bin/bash

input=$1

function fetch_latest_changes() {
  echo "Fetching the latest remote branches"
  git fetch --all
}

fetch_latest_changes

function find_feature_branches() {
  echo "Searching for feature branches"
  feature_branches=($(git branch -r --list "*-main" | sed 's|origin/||'))

  if [ ${#feature_branches[@]} -eq 0 ]; then
    echo "...none found"
    return
  fi

  for feature_branch in "${feature_branches[@]}"; do
    echo "...found feature branch: $feature_branch"
  done
}

find_feature_branches

function find_exempt_branches() {
  echo "Searching for exempt branches"
  IFS=',' read -r -a exempt_branches <<< "$input"

  if [ ${#exempt_branches[@]} -eq 0 ]; then
    echo "...none found"
    return
  fi

  for exempt_branch in "${exempt_branches[@]}"; do
    echo "...found exempt branch: $exempt_branch"
  done
}

find_exempt_branches

function filter_feature_branches() {
  echo "Filtering branches"
  branches=()

  for feature_branch in "${feature_branches[@]}"; do
    if ! [[ "${exempt_branches[*]}" =~ "$feature_branch" ]]; then
      echo "...including feature branch: $feature_branch"
      branches+=("$feature_branch")
    else
      echo "...excluding feature branch: $feature_branch"
    fi
  done
}

filter_feature_branches

function merge_main() {
  echo "Merging main into branches"

  if [ ${#branches[@]} -eq 0 ]; then
    echo "...no branches to merge into"
    return
  fi

  for branch in "${branches[@]}"; do
    echo "...switching to branch: $branch"
    git switch "$branch"
    echo "...merging main"
    git merge -m "misc: merge from main" main
    if [ $? -eq 0 ]; then
      echo "...pushing to origin"
#      git push origin "$branch"
# TODO: Enable pushing to origin
    else
      echo "...merge failed"
      git merge --abort
    fi
  done
}

merge_main