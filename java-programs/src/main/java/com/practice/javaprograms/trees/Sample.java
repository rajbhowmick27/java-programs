package com.practice.javaprograms.trees;

public class Sample {

    private static class Node{
        private int data;
        private Node left;
        private Node right;

        public Node(int data){
            this.data = data;
        }
    }

    private static void preorder(Node root){
        if(root == null)
            return;

        System.out.print(root.data + " ");
        preorder(root.left);
        preorder(root.right);
    }

    public static void main(String[] args) {
        Node root = new Node(2);
        root.left = new Node(4);
        root.right = new Node(5);
        root.left.left = new Node(6);
        root.left.right = new Node(7);

        preorder(root);
    }
}
